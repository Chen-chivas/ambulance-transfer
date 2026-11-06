# 救护车转运调度系统

根据 `文档存放/基础大纲.docx` 建立的 PC 优先项目。采用 Spring Boot + Vue 3 技术路线，数据库使用 **SQL Server**。

## 当前范围

- 后端：Spring Boot 3.5、Java 21、MyBatis、SQL Server、Spring Security、BCrypt、JWT、订单状态机。
- PC 前端：Vue 3、Vite、TypeScript、Vue Router、Pinia、Element Plus，登录、系统概览、车行管理、车辆与人员管理、模拟下单、订单池、派车调度、司机任务和订单监管。
- 数据库：六张核心表、外键、状态约束、报价唯一中标索引、订单时间轴索引。
- 小程序：仅预留目录。

已完成阶段 0–3：登录与角色隔离、车行资质审核、车辆与人员资料、调度及司机账号，以及模拟下单 → 报价/改价 → 用户选标 → 派车 → 出发 → 到达 → 上车 → 送达。管理员可查看订单、报价、资源指派和时间轴。取消及订单统计留待后续阶段。

调度员只能维护所属车行的车辆、人员与报价，无法查看其他车行的待选报价及资源指派。报价、选标和派车检查有效资质与车辆；派车同时检查人员证书、角色和空闲状态。选标与派车使用事务、行锁和条件更新防止重复中标或资源被重复分配；送达后释放车辆和人员。

## 项目目录

```text
backend/                 Spring Boot 后端
frontend-pc/             PC 调度工作台
frontend-miniprogram/    小程序预留
docs/                    架构、接口、数据库与开发路线
scripts/                 SQL Server 初始化和启动脚本
文档存放/                 原始需求文档
```

## 本地启动（Windows PowerShell）

环境：Windows x64、JDK 21 x64、Maven 3.9+、Node.js 22.12+、SQL Server 2019+、sqlcmd。运行后端数据库连接需要 SQL Server 启用 TCP/IP；默认使用 `localhost:1433`。命名实例或自定义端口请修改 `DB_URL`。

### 1. 初始化数据库

使用有建库权限的 Windows 账号，在项目根目录执行：

```powershell
.\scripts\init-db.ps1 -Server localhost
```

也可在 SSMS 中依次执行 `scripts/sqlserver/00-create-database.sql` 和 `01-schema.sql`。数据库名称为 `ambulance_transfer`。初始化不插入业务数据或默认账号；表脚本使用事务，发现已有核心表会拒绝重复执行，避免覆盖数据。已有数据库请先检查结构，后续变更使用独立迁移脚本。

### 2. 配置并启动后端

默认使用运行应用的 Windows 账号直接连接本机 `ambulance_transfer`，不需要填写 SQL Server 用户名和密码。在项目根目录执行：

```powershell
.\scripts\start-backend.ps1
```

首次使用需创建管理员，执行下列命令并按提示输入账号与密码（至少 10 个字符，最多 72 个 UTF-8 字节）：

```powershell
.\scripts\start-backend.ps1 -InitializeAdmin
```

系统仅在管理员不存在时创建首位管理员，不提供固定默认密码，也不会覆盖已有账号。后续正常启动无需此参数。也可通过 `BOOTSTRAP_ADMIN_USERNAME`、`BOOTSTRAP_ADMIN_PASSWORD` 环境变量完成初始化，用后清除。登录后的管理员在“车行管理 → 车辆 / 账号 → 调度账号”创建车行账号。

同一端口只运行一个后端。若要切换启动参数（如初始化管理员或启用模拟下单），先在原后端终端按 `Ctrl+C`，再重新启动。启动脚本会提前检查 `SERVER_PORT`（默认 8080），端口占用时在输入密码前给出明确提示。若直接使用 Maven 遇到 `BUILD FAILURE`，请向上查找 `APPLICATION FAILED TO START` 或 `Caused by`，Maven 的退出摘要本身不包含根因。

JWT 有效期两小时，凭据保存在当前标签页的 sessionStorage，退出登录会清除；关闭标签页通常会结束此会话，浏览器恢复会话时可能恢复存储。可通过 `JWT_SECRET` 设置至少 32 字节的固定签名密钥；未配置时每次启动随机生成，重启后旧登录失效。每次请求重新检查账号启用状态和角色，调度员车行身份从服务端账号读取。

Windows x64 下 Maven 自动启用 `windows-integrated-auth` 配置，从 Maven Central 获取与 JDBC 驱动匹配的 Microsoft 原生认证 DLL，放入 `backend/target/native`，并为 `spring-boot:run` 设置加载路径。首次启动需要下载依赖。当前 Windows 账号必须具备项目库访问权限；换电脑或换账号时需要重新授予相应数据库权限。

如之前设置过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，请清除当前终端中的旧值后启动，以使用默认 Windows 认证：

```powershell
Remove-Item Env:DB_URL, Env:DB_USERNAME, Env:DB_PASSWORD -ErrorAction SilentlyContinue
```

在 `backend` 目录运行打包后的应用时，显式指定原生库目录：

```powershell
mvn.cmd package
java "-Djava.library.path=target/native" -jar target/ambulance-transfer-0.0.1-SNAPSHOT.jar
```

IDE 直接运行主类时，先执行 `mvn.cmd process-resources`，再在 VM options 中设置 `-Djava.library.path=<项目绝对路径>/backend/target/native`。如改用 SQL Server 账号认证，将 `DB_URL` 中的 `integratedSecurity` 改为 `false`，并通过 `DB_USERNAME`、`DB_PASSWORD` 提供凭据。

`backend/.env.example` 是变量参考，不会被自动加载。`trustServerCertificate=true` 用于本地开发；部署时应配置可信证书并改为 `false`。后端默认监听 `127.0.0.1:8080`，数据库未配置时仍可启动，数据库健康检查将返回 503。

### 3. 启动 PC 前端

另开终端，在项目根目录执行：

```powershell
.\scripts\start-frontend.ps1
```

访问 http://localhost:5173 ，使用初始化的账号登录。开发服务器将 `/api` 代理到 `http://127.0.0.1:8080`。如果后端端口改变，请在启动前设置 `$env:API_PROXY_TARGET`。

系统概览中的后端服务与数据库均显示“已连接”时，前后端和 SQL Server 的连接验证完成。数据库健康检查仅验证连接与库名，不代替表结构初始化验证。

### 4. 验证下单、报价与选标

模拟下单默认关闭。开发时用以下命令启动后端（首次使用可同时加 `-InitializeAdmin`）：

```powershell
.\scripts\start-backend.ps1 -EnableMockOrders
```

也可设置环境变量 `MOCK_ORDERS_ENABLED=true`；该开关只控制模拟下单接口，关闭后订单监管和车行端已存在的订单记录仍可查询。本阶段不增加数据库表或修改已有结构，无需重新初始化数据库。

1. 管理员在“车行管理”创建车行，填写有效资质到期日并审核通过，登记空闲车辆、创建调度账号。
2. 管理员在“模拟下单”创建转运需求。此页面只显示当前管理员创建的订单，选标也只允许该创建者操作。
3. 在另一个浏览器或独立无痕窗口登录调度账号，进入“订单池”。仅展示本车行资质有效且有相同服务等级空闲车辆的开放订单，每 10 秒刷新。
4. 打开订单提交报价；在订单确认前可修改同一条报价。报价金额必须大于零且最多两位小数。
5. 返回模拟下单详情，等待自动刷新或手动刷新，选择报价并确认。若报价已改价，需重新确认；若车行资质或车辆条件失效，不能选中。
6. 订单转为“已确认”，一家车行中标、其他车行落标；调度员在“我的报价与中标”查询结果，管理员在“订单监管”查看记录。

### 5. 验证第三阶段派车与司机履约

1. 调度员进入“人员管理”，新增司机和护士，填写证书编号、到期日；司机另需设置登录账号和初始密码。管理员也可在“车行管理 → 车辆 / 账号 → 司机 / 护士”维护指定车行人员。
2. 调度员进入“派车调度”，默认查看已确认的待派车订单。打开详情后分配同等级空闲车辆、司机和护士；监护型必须配备护士，基础型可选配。
3. 点击“确认派车”。后台重新校验资质、证书及资源占用，成功后车辆进入执行任务状态，同一人员不能同时接受其他订单。
4. 在独立浏览器窗口登录司机账号，进入“司机任务”。依次确认出发、到达上车点、患者上车、送达；每一步需确认，禁止跳步或重复推进。
5. 送达后订单完成，车辆恢复空闲，司机和护士恢复可分配；指派信息和完整时间轴保留。任务中的人员不能编辑、停用或删除，有历史引用的人员不能删除。
6. 调度员清除状态筛选查看已派车及完成订单；管理员在“订单监管”跟进全流程。本阶段沿用现有六张表，无需重建数据库。

## 验证

```powershell
cd backend
mvn.cmd test
mvn.cmd package -DskipTests
cd ../frontend-pc
npm.cmd ci
npm.cmd run build
npm.cmd run test:e2e
```

浏览器测试使用本机 Microsoft Edge，以隔离的模拟接口验证登录、权限、下单、报价、选标、人员表单、监护型派车校验、资源冲突刷新和司机四节点操作，不依赖业务数据库。自动启动测试端口 5174。

使用已初始化 SQL Server 运行真实数据库集成测试（Windows 集成认证与后端相同）：

```powershell
cd backend
$env:RUN_SQLSERVER_TESTS = 'true'
mvn.cmd test
Remove-Item Env:RUN_SQLSERVER_TESTS
```

集成测试覆盖真实登录与 JWT、基础资料、权限隔离、订单创建/报价/改价/选标、派车资质与人员证书重检、司机顺序推进、完整时间轴和送达资源释放。并发测试验证 10 次选标仅成功一次，以及不同订单竞争同一车辆、司机或护士仅一次派车成功。普通用例结束后回滚；并发用例提交独立测试数据后在 finally 中清理。SQL Server 自增序列会产生正常空号。未设置开关时跳过数据库集成测试。

健康接口：`GET http://localhost:8080/api/health` 返回 `{"status":"ok"}`；`GET /api/health/db` 实际连接 SQL Server，成功返回库名，失败返回 503。

下一阶段完善订单取消、取消原因及取消时的资源释放，再补充费用与统计。生产部署关闭模拟下单，并按实际环境配置域名、HTTPS 和固定签名密钥。

更多说明：[架构与阶段安排](docs/architecture.md)、[数据库设计](docs/database.md)、[接口清单](docs/api.md)。
