# API 清单

开发基址：`http://localhost:8080/api`。前端开发使用 Vite `/api` 代理。

## 已实现

| 方法 | 路径 | 响应 |
| --- | --- | --- |
| GET | /health | 200：`{"status":"ok"}`，仅检查服务存活 |
| GET | /health/db | 200：`{"status":"ok","database":"ambulance_transfer"}`；连接失败或库名不符返回 503 |
| POST | /auth/login | 账号密码登录，返回 token、expiresAt、user |
| GET | /auth/me | 返回当前用户摘要 |
| GET | /admin/overview | 车行数、待审数、有效已通过数、车辆数、空闲车辆数 |
| GET / POST | /admin/companies | 分页查询 / 新增车行 |
| GET / PUT / DELETE | /admin/companies/{id} | 查询 / 更新并重新送审 / 删除无关联车行 |
| POST | /admin/companies/{id}/audit | 待审核资料通过或驳回 |
| GET / POST | /admin/companies/{id}/dispatchers | 查询 / 创建该车行调度账号 |
| GET / POST | /admin/companies/{companyId}/vehicles | 分页查询 / 新增车辆 |
| PUT / DELETE | /admin/companies/{companyId}/vehicles/{id} | 编辑 / 删除非任务车辆 |
| GET | /company/profile | 当前调度员所属车行资料 |
| GET / POST | /company/vehicles | 分页查询 / 新增本车行车辆 |
| PUT / DELETE | /company/vehicles/{id} | 编辑 / 删除本车行非任务车辆 |
| GET | /admin/features | `{mockOrdersEnabled: boolean}`，模拟下单开关 |
| GET | /admin/orders | 全局订单分页检索 |
| GET | /admin/orders/{id} | 订单、全部报价及状态/报价记录 |
| GET / POST | /mock/orders | 当前管理员创建的模拟订单 / 新建需求；需启用开发开关 |
| GET | /mock/orders/{id} | 当前管理员的订单详情、报价及记录 |
| POST | /mock/orders/{id}/select | 按报价 id 和确认金额选标 |
| GET | /company/orders/pool | 资质与车辆等级匹配的开放订单 |
| GET | /company/orders/mine | 本车行已报价订单，包含中标/落标结果 |
| GET | /company/orders/{id} | 可见订单详情、本车行报价及状态记录 |
| POST | /company/orders/{id}/quotes | 提交或修改本车行待选报价 |
| GET / POST | /company/staff | 分页查询 / 新增本车行司机或护士 |
| PUT / DELETE | /company/staff/{id} | 修改 / 删除本车行空闲人员 |
| GET / POST | /admin/companies/{companyId}/staff | 分页查询 / 新增指定车行人员 |
| PUT / DELETE | /admin/companies/{companyId}/staff/{id} | 修改 / 删除指定车行空闲人员 |
| GET | /company/orders/assigned | 本车行中标订单，支持状态等分页筛选 |
| GET | /company/orders/{id}/resources | 待派车订单的可用车辆、司机和护士 |
| POST | /company/orders/{id}/dispatch | 分配资源，订单转为 DISPATCHED |
| GET | /driver/tasks | 当前司机被指派的任务，支持订单分页筛选 |
| GET | /driver/tasks/{id} | 当前司机的任务详情与状态时间轴 |
| POST | /driver/tasks/{id}/depart | DISPATCHED → EN_ROUTE |
| POST | /driver/tasks/{id}/arrive | EN_ROUTE → ARRIVED |
| POST | /driver/tasks/{id}/pickup | ARRIVED → ON_BOARD |
| POST | /driver/tasks/{id}/complete | ON_BOARD → COMPLETED，释放资源 |

数据库检查不会对外暴露连接密码或原始 SQL 异常。基础业务错误响应约定为 `code`、`message`、`timestamp`，HTTP 状态反映失败原因。

除健康检查与登录外，所有接口需要 `Authorization: Bearer <token>`。`/admin/**` 和 `/mock/**` 限 ADMIN，`/company/**` 限 DISPATCHER，`/driver/**` 限 DRIVER。401 表示未登录、账号停用或 token 失效，403 表示角色不符；不存在或无权访问的资源返回 404。创建成功为 201，删除成功为 204，参数错误为 400，重复记录或业务冲突为 409，数据库不可用为 503。

分页参数：`page=1`、`size=10`（1–100）、`keyword`（最多 100 字符）、`status`。响应 `{items,total,page,size}`，按 id 倒序。车行 status 为 `PENDING/APPROVED/REJECTED/EXPIRED`；车辆为 `IDLE/TASK/MAINTENANCE`。关键词为字面包含搜索，不将 `%` 当通配符。

请求示例：

```json
// POST /auth/login
{"username":"your-account","password":"your-password"}
// POST /admin/companies 或 PUT /admin/companies/{id}
{"name":"示例车行","licenseNo":"LICENSE-001","licenseExpireAt":"2027-12-31","contactPhone":"13800000000"}
// POST /admin/companies/{id}/audit
{"status":"REJECTED","remark":"请补充资质材料"}
// POST .../vehicles 或 PUT .../vehicles/{id}
{"plateNo":"京A12345","serviceLevel":"BASIC","status":"IDLE"}
// POST /admin/companies/{id}/dispatchers
{"name":"调度员","username":"dispatcher01","password":"at-least-10-characters","phone":"13800000000"}
```

登录名 3–50 位字母、数字、点、横线或下划线；新账号密码至少 10 字符且最多 72 UTF-8 字节。车行名称与许可证号最多 100 字符，车牌最多 20 字符，审核备注最多 500 字符。服务等级为 `BASIC/MONITORING`，车辆写入只允许 `IDLE/MAINTENANCE`，TASK 留给履约服务。

修改车行会重置为待审核；审核通过需要有效到期日，驳回原因必填。车行详情和分页查询会将已过期的通过资质显示为 EXPIRED。已有车辆、人员或历史业务的车行不能删除；有历史引用的车辆不能删除。

## 下单、报价与选标

模拟接口默认关闭，需设置 `MOCK_ORDERS_ENABLED=true` 或运行 `scripts/start-backend.ps1 -EnableMockOrders`。关闭时 `/mock/orders` 返回 404 / `MOCK_DISABLED`。订单归属当前管理员账号，仅创建者能在模拟端查询和选标；监管接口可由管理员查看全部订单。

```jsonc
// POST /mock/orders -> 201，返回新订单
{
  "userName": "张先生",
  "userPhone": "13800000000",
  "originAddress": "第一医院住院部",
  "destAddress": "第二医院康复中心",
  "serviceLevel": "BASIC",
  "patientNote": "需要轮椅"
}
// POST /company/orders/{id}/quotes -> 200，创建或更新当前车行报价
{"amount": 350.50, "remark": "包含基础转运服务"}
// POST /mock/orders/{id}/select -> 200，返回 CONFIRMED 订单
{"quoteId": 12, "amount": 350.50}
```

联系人最多 50 字符，地址各最多 255 字符且不能相同，备注最多 1000 字符。报价为正数，最多 10 位整数、2 位小数，报价说明最多 500 字符。选标金额必须与当前报价完全相等，否则返回 409 并要求刷新重新确认。

订单列表继续使用 `{items,total,page,size}`，支持 `page`、`size`、`keyword`（订单号/起点/终点）、`serviceLevel`、`status`。状态为 `PENDING_QUOTE/QUOTING/CONFIRMED/DISPATCHED/EN_ROUTE/ARRIVED/ON_BOARD/COMPLETED/CANCELLED`。池仅返回前两种开放状态，且资质须审核通过、到期日不早于当天、有精确相同等级的 IDLE 车辆。资质日期按 Asia/Shanghai 判断，界面时间按本地时区展示。

订单详情结构为 `{order,quotes,timeline}`。订单含 `assignedCompanyId`、`assignedCompanyName`、`finalAmount`；车行列表额外含 `myQuoteId`、`myAmount`、`myQuoteStatus`。报价含 `companyName`、`amount`、`status`（PENDING/WON/LOST）与实时 `eligible`。资格失效后已有报价保留，但不可选中；选标时再次校验。车行详情仅提供本车行报价，时间轴不返回其他车行报价留痕。

同车行同订单重复报价更新原报价；首次有效报价推动 PENDING_QUOTE → QUOTING，后续改价不再新增状态转换。每次报价写 FEE_CHANGE 记录。选标在单事务中锁订单，更新 CONFIRMED、车行与金额、报价 WON/LOST 及状态记录；重复或并发败选请求返回 409。选标成功暂不占用车辆，派车时重新检查并占用。

## 人员、派车与司机履约

```jsonc
// POST /company/staff，管理员的指定车行接口使用相同结构
{"name":"王师傅","phone":"13800000000","role":"DRIVER","certNo":"CERT-001","certExpireAt":"2027-12-31","username":"driver01","password":"at-least-10-characters"}
// 护士 role=NURSE，不提交 username/password
// PUT /company/staff/{id}；角色和登录名不可修改
{"name":"王师傅","phone":"13800000000","certNo":"CERT-001","certExpireAt":"2027-12-31","enabled":true}
// POST /company/orders/{id}/dispatch -> 200，返回派车后的订单
{"vehicleId":20,"driverId":30,"nurseId":40}
// 基础型可将 nurseId 省略或设为 null；监护型必填
// POST /driver/tasks/{id}/depart、arrive、pickup、complete 均无请求体
```

人员列表支持 `page/size/keyword/role`，role 为 DRIVER/NURSE 或空，关键词搜索姓名、电话、证书编号。响应含 `id/companyId/name/phone/role/username/certNo/certExpireAt/enabled/busy`，不返回密码及哈希。姓名最多 50 字符，证书编号最多 100 字符，到期日必填；允许登记已过期资料，但不可用于派车。司机需设置登录名和密码，约束与调度账号一致；护士无需登录。执行任务中的人员不可修改、停用或删除；历史引用阻止删除。

资源接口返回 `{vehicles,drivers,nurses}`，仅允许中标车行查看 CONFIRMED 订单的可用资源。派车重检车行已审核且资质有效、车辆精确匹配服务等级且为 IDLE、人员属于本车行且角色正确、已启用、证书编号非空、证书未过期、未执行其他任务。司机须有登录账号。监护型必须配置护士。

派车在单事务中依次锁订单、车行、车辆和人员，条件更新车辆为 TASK、订单为 DISPATCHED、写状态日志。进行中的 DISPATCHED/EN_ROUTE/ARRIVED/ON_BOARD 订单占用司机和护士；并发竞争返回 409，客户端应刷新资源并重新选择。完成时原子更新订单为 COMPLETED、车辆为 IDLE，人员随订单完成恢复可用。已派任务允许在资质或证书后来过期时正常履约。

司机仅能查询和推进本人且同车行的指派任务；不可跳步或重复推进。详情的 quotes 为空，不返回报价费用日志，列表的 myQuoteId/myAmount/myQuoteStatus 为空。订单指派字段包含 assignedVehicleId/assignedDriverId/assignedNurseId、assignedPlateNo/assignedDriverName/assignedNurseName。落标车行的订单列表和详情隐藏中标车行的车辆及人员指派字段。

## 后续规划（尚不可调用）

| 模块 | 接口方向 |
| --- | --- |
| 订单统计 | /admin/stats |
| 取消 | 模拟用户取消、取消原因与资源释放 |

地图路径、支付和小程序授权仍暂缓。
