# SQL Server 数据库设计

数据库：`ambulance_transfer`；默认 schema：`dbo`。建表源文件位于 `scripts/sqlserver/01-schema.sql`。

| 表 | 用途 | 主要约束 |
| --- | --- | --- |
| car_company | 车行、资质、审核 | 许可证唯一，驳回原因必填 |
| vehicle | 车辆、服务等级、模拟位置 | 车牌唯一、所属车行外键 |
| staff | 管理员、调度员、司机、护士 | 可空登录名的筛选唯一索引、角色与车行约束 |
| transfer_order | 转运订单、指派、金额 | 订单号唯一、同车行复合外键、ROWVERSION |
| quote | 车行报价 | 每车行每订单一条报价，每订单最多一个 WON |
| order_status_log | 状态与费用变更留痕 | 订单外键、按订单/时间/id 查询索引 |

## 与原文的差异

- `order` 改为 `transfer_order`，避免 SQL 关键字冲突。
- 主键使用 `BIGINT IDENTITY(1,1)`；中文使用 `NVARCHAR`，SQL 中文字面量使用 `N'文本'`。
- 金额使用 `DECIMAL(12,2)`，不使用浮点类型。
- 时间使用 `DATETIME2(3)`，默认 `SYSUTCDATETIME()`，约定保存 UTC；前端展示时转换为用户时区。`updated_at` 需由后续更新语句显式维护。
- 并发版本字段使用 `ROWVERSION`，它不是时间戳，业务更新可用旧版本作为条件。
- 增加资质到期日、登录名/密码哈希、实际金额、费用备注、空驶费标记。初始化 SQL 不创建默认账号；首位管理员通过启动选项创建。
- 状态统一大写：审核 `PENDING/APPROVED/REJECTED/EXPIRED`，车辆 `IDLE/TASK/MAINTENANCE`，报价 `PENDING/WON/LOST`，服务等级 `BASIC/MONITORING`。
- 日志 `event_type=FEE_CHANGE` 允许前后状态相同，费用变更明细由后续服务写入 `remark`。

## 一致性边界

外键采用默认 NO ACTION，避免删除车行或订单时级联抹去审计数据。司机/护士复合外键确保人员与订单车行相同；具体角色、证书、资源是否占用仍由事务服务校验。

`UX_quote_one_winner` 只保证最多一个中标报价；业务服务仍需原子更新订单及全部报价。车辆和人员占用、状态日志完整性由业务事务保证，取消原因及进一步费用规则待后续实现，建表本身不会自动生成日志。

阶段 1 直接使用现有六张表，无需重新初始化或迁移。车行审核、车辆修改与账号创建使用事务和行锁；重复许可证、车牌和登录名返回 409，外键关联阻止误删历史资料。已通过但到期的资质在查询中计算为 EXPIRED，数据库保留原审核值。

阶段 2 继续使用原结构。模拟订单归属取自首条 `from_status IS NULL`、`operator_type='MOCK_USER'` 的日志 `operator_id`，其值为创建时登录管理员 id。每次报价与改价均保留 FEE_CHANGE 日志；首次报价和选标分别写 STATUS_CHANGE。订单更新锁在整个报价/选标事务内保持，选标条件要求订单仍为 QUOTING，报价状态和金额在同一事务同步。原唯一索引继续保证一个订单最多一个 WON。

阶段 3 无结构变更，使用 staff.cert_no/cert_expire_at/enabled 与已有登录字段，以及 transfer_order 的 assigned_vehicle_id/assigned_driver_id/assigned_nurse_id。车辆派车时从 IDLE 条件更新为 TASK，送达时反向更新；人员不新增状态列，通过进行中的指派订单计算 busy，完成后自动恢复可用。订单和资源更新与状态日志处于同一事务，车行更新锁防止同车行资源被并发重复分配。可空日期和护士 id 使用明确 JDBC 类型绑定，兼容 SQL Server 空值参数。

每家车行每个订单保留一条报价，在报价仍为 `PENDING` 且订单开放时可修改；现有 FEE_CHANGE 日志记录每次报价及改价金额，独立的结构化报价版本表仍未实现。

## 初始化与迁移

先运行建库脚本，再运行建表脚本；建表整体使用事务，失败回滚。脚本不会清空已有数据库；已有 `car_company` 时直接报错。后续结构变更使用新增版本脚本，不重复运行初始建表脚本。

分页使用 `ORDER BY ... OFFSET ... ROWS FETCH NEXT ... ROWS ONLY`；取前 N 行使用 `TOP`，不使用 MySQL 的 `LIMIT`、反引号或 `AUTO_INCREMENT`。MyBatis 插入自增主键可配置 `useGeneratedKeys=true`。
