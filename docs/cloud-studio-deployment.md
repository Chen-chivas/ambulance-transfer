# Cloud Studio 部署记录

> 本文档记录本项目在 **Cloud Studio（Linux 容器）** 上的完整部署过程。
> 与 README.md 中的 Windows 本地部署方式并列，两者互不影响。

---

## 一、与本地部署的关键差异

| 项 | Windows 本地 | Cloud Studio（Linux） |
|---|---|---|
| JDK | 21 | **需手动安装**（默认只有 20） |
| SQL Server | 本机已安装（集成认证） | **Docker 容器 + SQL 账号认证** |
| 数据库认证 | `integratedSecurity=true` | 必须去掉，改用 `sa` 账号 |
| 后端地址 | 127.0.0.1:8080 | 同上，但需端口转发才能从浏览器访问 |
| CORS | 默认 `localhost:5173` 够用 | **必须放开**，否则登录返回 403 |

---

## 二、部署步骤

### 1. 安装 JDK 21

系统默认是 JDK 20，但项目编译目标是 **Java 21**（`pom.xml` 中 `<java.version>21</java.version>`），
且代码使用了 Java 21 的 `SequencedCollection` API（`OrderService.java` 中的 `List.getFirst()`），
**无法用 JDK 20 编译或运行**。

```bash
apt-get update && apt-get install -y openjdk-21-jdk
```

验证：

```bash
ls /usr/lib/jvm/
# 应看到 java-21-openjdk-amd64
```

### 2. 启动 SQL Server 容器

项目使用 SQL Server 专有语法（`WITH (UPDLOCK, HOLDLOCK)`、`COUNT_BIG`、`SYSUTCDATETIME()`、
`OFFSET ... FETCH NEXT`、`dbo.` schema 等），**无法替换为 MySQL/PostgreSQL**，必须使用 SQL Server。

```bash
docker pull mcr.microsoft.com/mssql/server:2022-latest

docker run -d --name sqlserver \
  -e "ACCEPT_EULA=Y" \
  -e "MSSQL_SA_PASSWORD=Ambulance@2026" \
  -p 1433:1433 \
  --restart unless-stopped \
  mcr.microsoft.com/mssql/server:2022-latest
```

等待就绪（约 30 秒）：

```bash
docker logs sqlserver 2>&1 | grep -i "ready for client connections"
```

### 3. 初始化数据库

```bash
docker cp scripts/sqlserver/00-create-database.sql sqlserver:/tmp/
docker cp scripts/sqlserver/01-schema.sql sqlserver:/tmp/

docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P 'Ambulance@2026' -C -b \
  -i /tmp/00-create-database.sql

docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P 'Ambulance@2026' -C -b \
  -i /tmp/01-schema.sql
```

验证 6 张表：

```bash
docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P 'Ambulance@2026' -C -d ambulance_transfer \
  -Q "SELECT name FROM sys.tables ORDER BY name"
```

预期：`car_company`、`order_status_log`、`quote`、`staff`、`transfer_order`、`vehicle`

### 4. 编译后端

```bash
cd backend
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -DskipTests package
```

> **`JAVA_HOME` 前缀不能省**，否则 Maven 会用默认的 JDK 20 编译并报 `invalid target release: 21`。

### 5. 启动后端

数据库认证方式与本地不同，必须通过环境变量覆盖 `DB_URL`：

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=ambulance_transfer;encrypt=true;trustServerCertificate=true;loginTimeout=10"
export DB_USERNAME=sa
export DB_PASSWORD='Ambulance@2026'
export MOCK_ORDERS_ENABLED=true
export CORS_ALLOWED_ORIGINS="*"

$JAVA_HOME/bin/java -jar target/ambulance-transfer-0.0.1-SNAPSHOT.jar
```

成功标志：

```
Tomcat started on port 8080 (http)
Started AmbulanceTransferApplication in X.XXX seconds
HikariPool-1 - Start completed.
首位管理员已创建。   ← 仅首次
```

### 6. 启动前端

```bash
cd frontend-pc
npm ci        # 首次需要，node_modules 未入库
npm run dev
```

访问 `http://127.0.0.1:5173`，用管理员账号登录。

---

## 三、必须留意的三个坑

### 坑 1：CORS 导致登录 403

`application.yml` 的默认白名单只有：

```yaml
allowed-origins: http://localhost:5173,http://127.0.0.1:5173
```

通过 Cloud Studio 的预览域名访问时，浏览器会带上 `Origin: https://xxx.cloudstudio.club`，
不在白名单内，Spring Security 直接返回 **403**（而不是 401）。

**现象**：`curl` 直连 8080 能登录成功，但浏览器登录报「请求失败 (403)」。

**解决**：启动时设 `CORS_ALLOWED_ORIGINS`。生产环境建议填精确域名而非 `*`。

### 坑 2：管理员密码只在首次创建时生效

`BOOTSTRAP_ADMIN_PASSWORD` 仅在 `staff` 表中**不存在 ADMIN 记录**时使用。
之后无论怎么改环境变量，**已存在的密码不会被更新**。

如需重置密码，可直接更新数据库（BCrypt 哈希）：

```bash
# 生成哈希（任意 60 字符的 BCrypt 串即可，$2a$ 开头）
# 或用 Spring Security 的 BCryptPasswordEncoder.encode("新密码")

docker exec sqlserver /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P 'Ambulance@2026' -C -d ambulance_transfer \
  -Q 'UPDATE dbo.staff SET password_hash = ''$2a$10$....'' WHERE username = ''admin'''
```

### 坑 3：构建产物不入库

`.gitignore` 排除了 `backend/target/` 和 `frontend-pc/node_modules/`，
所以**每次在新环境首次部署都必须重新 `mvn package` 和 `npm ci`**。

---

## 四、重启工作空间后的恢复步骤

工作空间重启后，容器会随 `--restart unless-stopped` 自动恢复，但 **Java 进程和 Vite 需要手动重启**：

```bash
# 1. 确认 SQL Server 活着
docker ps --format "table {{.Names}}\t{{.Status}}"

# 2. 启动后端（终端 A）
bash /workspace/ambulance-transfer/run-backend.sh

# 3. 启动前端（终端 B）
cd /workspace/ambulance-transfer/frontend-pc && npm run dev
```

> 若工作空间被**完全销毁重建**，则需要从「二、部署步骤」第 1 步重新开始
> （JDK、容器、数据库都会丢失）。

---

## 五、验证接口清单

| 接口 | 认证 | 预期 |
|---|---|---|
| `GET /api/health` | 否 | `{"status":"ok"}` |
| `GET /api/health/db` | 否 | `{"status":"ok","database":"ambulance_transfer"}` |
| `POST /api/auth/login` | 否 | 返回 JWT token + 用户信息 |
| `GET /api/auth/me` | 是 | 当前用户信息 |
| `GET /api/admin/**` | ADMIN | 管理员接口 |
| `GET /api/company/**` | DISPATCHER | 调度员接口 |
| `GET /api/driver/**` | DRIVER | 司机接口 |

> 权限规则见 `SecurityConfig.java`：**默认 `anyRequest().denyAll()`**，
> 白名单显式放行，属于安全的默认拒绝设计。

---

## 六、演示账号

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `Admin@12345` |

> 调度员、司机账号需由管理员在「车行管理 → 账号」中创建。
> 护士无需登录账号。

---

*部署验证日期：2026-11-06*
