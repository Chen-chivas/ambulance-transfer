# 后端

Spring Boot 3.5 / Java 21 / MyBatis / Microsoft SQL Server JDBC。

已实现健康检查、JWT 登录、角色权限、基础资料 CRUD 与审核、基础统计，以及订单创建、报价与选标、人员与司机账号、派车、司机四节点、送达资源释放和状态日志。默认监听 `127.0.0.1`。

按以下结构扩展实际业务代码，避免创建无实现的 Controller：

```text
com.ambtransfer
├── controller/{admin,company,driver,mock}
├── service/{admin,company,driver,order}
├── dao/db/{company,staff,order,system}
├── entity
├── dto/{request,response}
├── enums
├── config
└── exception
```

Controller 负责参数与身份校验；Service 负责业务事务；Mapper 使用参数化 SQL 访问 SQL Server。多表修改在同一 `@Transactional` 事务内完成。实现数据库更新时必须同时使用状态条件/版本检查及状态日志，内存状态机本身不提供并发控制。

Spring Security 使用 HS256 JWT 验签与有效期校验，BCrypt cost=12 保存密码。业务接口默认拒绝未授权访问；管理员访问 `/admin/**`，调度员访问 `/company/**`，所属车行由服务端账号读取，每次请求检查当前角色与启用状态。登录返回用户摘要，密码哈希不进入响应。

首位管理员通过根目录 `scripts/start-backend.ps1 -InitializeAdmin` 或 `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` 环境变量创建，事务锁避免并发初始化，已有管理员时跳过。`JWT_SECRET` 至少 32 字节；未设置时使用随机启动密钥。默认没有预设账号密码。

车行修改后重新送审；仅允许审核待审资料，通过前校验到期日，查询时动态显示过期状态。车辆修改锁定所属车行与车辆行，不允许手工写入 TASK，也不允许修改或删除 TASK 车辆。历史关联使用数据库外键保护。

默认 `mvn.cmd test` 不连接业务数据库。设置 `RUN_SQLSERVER_TESTS=true` 后追加 SQL Server 集成测试。普通用例事务回滚；并发用例提交独立测试数据后在 finally 清理，不清空现有业务数据。

模拟下单通过 `MOCK_ORDERS_ENABLED=true` 启用，默认关闭；仅管理员可以创建、查看自己创建的模拟订单并选标。归属依据创建日志中的 `MOCK_USER + operator_id`，沿用六张表，无需迁移。管理员订单监管可查询全局订单。

车行订单池仅开放给资质有效且有相同服务等级 IDLE 车辆的车行。报价与选标统一按“订单 → 车行 → 匹配车辆”顺序加锁；同一订单报价串行写入，首笔有效报价进入 QUOTING，重复报价修改原记录。选标核对客户端确认的金额并重新校验资格，同时确认订单、结算全部报价并写日志。选标不占用资源，派车时重新校验并占用。

StaffService 提供司机与护士管理；DispatchService 负责订单 → 车行 → 车辆 → 人员的资源校验与占用。监护型必配护士，人员必须证书有效且空闲；进行中的订单计算人员 busy。司机仅能按顺序推进自己的任务；完成时原子释放车辆和人员。执行任务中的人员禁止修改或停用，历史引用禁止删除。跨车行资源、非本人司机操作、重复和跳步操作均拒绝。司机接口为 `/driver/**`，仅 DRIVER 角色可访问。

订单详情短事务持有订单更新锁，保证订单、报价和日志读取一致；车行详情只返回本车行报价及状态事件，不返回其他车行的报价费用日志。报价变更以 FEE_CHANGE 记录，无状态变化；状态变更始终经状态机和条件更新。

默认使用 Windows 集成认证连接本机 SQL Server，不需要数据库密码。Windows x64 下 Maven 会准备与 JDBC 驱动版本一致的认证 DLL，`mvn.cmd spring-boot:run` 自动设置原生库加载路径。打包启动及 IDE 配置见根目录 README。

`.env.example` 仅为环境变量清单，Spring Boot **不会自动加载 `.env`**；需要覆盖默认连接时，通过 PowerShell 或 IDE 设置运行环境变量。
