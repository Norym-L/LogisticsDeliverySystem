# 物流配送管理系统 · 接口文档

> 版本：v1.3（2026-11-28，新增轨迹地图）
> 后端：Spring Boot 4.1.2 + MyBatis 4.1.0 + MySQL 8（宿主机 3308）

---

## 1. 概述

### 1.1 启动

```bash
# 数据库（首次）
mysql -h127.0.0.1 -P3308 -uroot -p < sql/schema.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/seed.sql

# 已有旧库升级（按需，均可重复执行）
mysql -h127.0.0.1 -P3308 -uroot -p < sql/alter_pricing.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/alter_final_six.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/alter_track_map.sql

# 应用
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

默认端口 8080。若环境注入了 `SERVER_PORT`，用上面的参数覆盖。

### 1.2 Base URL

```
http://127.0.0.1:18080/api
```

### 1.3 角色

| 角色 | 说明 | 登录入口 |
|---|---|---|
| 用户 | 寄件人 / 收件人，不登录 | 无 |
| `PATH_ADMIN` | 路径管理员：审批、选路线 | `/auth/operator/login` |
| `STATION_ADMIN` | 节点管理员：始发收件核价、车次、发运、到货、派单 | `/auth/operator/login` |
| `POSTMAN` | 快递员：始发站收件核价、最后一公里取件/妥投/异常 | `/auth/postman/login` |

---

## 2. 通用约定

### 2.1 响应结构

所有接口统一返回：

```json
{ "code": 0, "message": "ok", "data": { } }
```

- `code = 0` 表示成功，其余为失败。
- `code` 与 HTTP 状态码一致（401、403、404、409 等），前端只判断 HTTP 状态也可以。
- 序列化配置为 `non_null`：值为 `null` 的字段不会出现在响应里。

### 2.2 错误码

| code | 含义 | 典型场景 |
|---|---|---|
| 400 | 参数不合法 | 手机号格式错、车次为空、起止站相同、未录入体积质量 |
| 401 | 未登录 | 未携带会话访问管理端/快递员端 |
| 403 | 无权操作 | 角色不符、跨站操作、快递员查了别人的单、寄件人手机号不符 |
| 404 | 资源不存在 | 订单号或快递员不存在 |
| 409 | 状态冲突 | 重复签收/妥投、已发运仍取消、未收件就发运、状态已被并发修改 |
| 500 | 服务端异常 | 未预期的错误 |

### 2.3 鉴权

管理端与快递员端使用 **Session**（`JSESSIONID` Cookie）：

1. 调登录接口，服务端返回 `Set-Cookie`；
2. 之后每个请求带上该 Cookie；
3. `/api/admin/**` 仅管理员可访问，`/api/postman/**` 仅快递员可访问；
4. 未登录返回 `401`，角色不符返回 `403`。

用户端、驿站列表、登录接口、运费试算公开，不需要 Cookie。

### 2.4 其他

- 时间格式为 ISO-8601，如 `2026-11-16T11:15:51`。
- 枚举以字符串返回，如 `state = "DELIVERING"`。
- 所有金额/重量用小数，`weight` 单位为千克，体积 `volumeCm3` 单位为立方厘米，运费单位为元。

### 2.5 运费计价与最终核价

体积重 = 体积（立方厘米）÷ 6000。计费重量取实际质量与体积重的较大值，再按顺丰内地互寄规则进位：

- 不足 10 kg：按 0.1 kg 向上进位
- 10 kg 到 100 kg 以下：按 0.5 kg 向上进位
- 100 kg 及以上：四舍五入到整千克
- 进位后不足 1 kg 按 1 kg 首重

运费 = 首重费 + max(0, 计费重量 - 1) × 续重费率，再四舍五入到整元。

里程分档（配置项 `logistics.pricing`，可改）：

| 里程 | 首重（1 kg） | 续重（每千克） |
|---|---|---|
| 200 km 及以内 | 18 元 | 5 元 |
| 大于 200 km 且不超过 1000 km | 22 元 | 8 元 |
| 大于 1000 km | 23 元 | 10 元 |

**最终价只核一次，且在始发站收件时核。** 流程：

1. 用户下单可填质量和体积，系统按最短路写入预估价 `quotedPrice`；只填其中一项则不算价（体积仍可存）。
2. 路径管理员批准后，订单进入待始发收件，此时还没有最终运费。
3. 始发站管理员或本站快递员确认收件时，录入/核对实测体积和质量；系统按**已固化路线**里程计算最终运费，写入 `price`、`priceConfirmedAt` 并锁定。
4. 未完成核价的订单不能发运。
5. 目的站快递员取件时不再改价；`POST /postman/orders/{no}/price` 只做只读复核。退回单同样在始发站（原目的站）收件时核价，不沿用原单最终运费。

未传实测值时，收件接口会退回用户申报值；申报值也不完整则返回 400，要求现场录入。

### 2.6 预计送达时效

规则简单、可复现，不接真实路由预测或外部 API：

- 预计天数 = max(1, 路线段数)，即**每跨一站记 1 日，最少 1 日**。
- 下单试算与未批准订单用最短路；批准后订单用 `order_route` 固化路线。
- 订单详情中的预计送达时间 = 下单时间 + 预计天数（简化值，不是承诺时效）。
- 批准时调整途经站会改变路线段数，订单详情的预计时效随之更新。

举例：北京 → 上海最短 5 站、4 段，预计 4 天；若管理员批准一条 7 站、6 段的绕行路线，则显示 6 天。

### 2.7 运费试算（公开，不写库）

```
POST /api/pricing/quote
```

请求：`weightKg`、`volumeCm3` 必填且必须大于 0；另需提供 `fromStationId` + `toStationId`，或 `orderNo`（有单号时用该单起止站 / 已固化路线算里程，体积质量以本次请求为准）。两者都缺时返回 400。

```json
{ "weightKg": 2.4, "volumeCm3": 12000, "fromStationId": 1, "toStationId": 8 }
```

响应 `data`：

```json
{ "weightKg": 2.4, "volumeCm3": 12000, "volumetricWeightKg": 2.0,
  "chargeableWeightKg": 2.4, "distanceKm": 1357,
  "firstKgPrice": 23, "additionalKgPrice": 10, "additionalWeightKg": 1.4,
  "freight": 37, "estimatedDays": 4 }
```

有单号试算时还会带上 `orderNo`。`estimatedDays` 按 2.6 的规则计算。

---

## 3. 账号与登录

### 3.1 种子账号

| 用户名 | 角色 | 所属站 | 初始密码 |
|---|---|---|---|
| admin | PATH_ADMIN | — | 123456 |
| st001 ~ st008 | STATION_ADMIN | ST001 ~ ST008 | 123456 |
| pm001 ~ pm016 | POSTMAN | 每站 2 人 | 123456 |

密码存储为 `SHA-256(salt + 明文)`，`salt = MD5(username)` 前 16 位。

### 3.2 管理员登录

```
POST /api/auth/operator/login
```

请求：

```json
{ "username": "st001", "password": "123456" }
```

响应：

```json
{ "code": 0, "message": "ok",
  "data": { "id": 2, "username": "st001", "name": "李京津",
            "role": "STATION_ADMIN", "stationId": 1 } }
```

### 3.3 快递员登录

```
POST /api/auth/postman/login
```

请求与响应结构同上，`role` 固定为 `POSTMAN`。

### 3.4 登出 / 当前身份

```
POST /api/auth/logout      // 销毁会话
GET  /api/auth/me          // 返回当前登录人，未登录 401
```

---

## 4. 订单状态机

```
WAITING ──批准──→ AWAITING_RECEIPT ──始发站确认收件并核价──→ APPROVED
   │                                                         │
   │                                              首次发运 ──→ SENDING
   │                                                         │
   │                                          快递员取件 ──→ DELIVERING
   │                                                         ├──签收/妥投──→ RECEIVED
   │                                                         └──拒收──────→ REFUSED
   │                                          SENDING ←──派送失败（回到目的站待再派）
   ├──拒绝/超时──→ REJECTED ──重提──→ WAITING
   └──用户取消（首次发运前）──→ CANCELLED
```

| 状态 | 含义 | 终态 |
|---|---|---|
| WAITING | 待审批 | 否 |
| AWAITING_RECEIPT | 已批准，等待始发站确认收件；路线已固化但未发运 | 否 |
| APPROVED | 始发站已确认收件并完成最终核价，等待首次发运 | 否 |
| REJECTED | 已拒绝（管理员拒绝或超时），可修改重提 | 否 |
| SENDING | 运送中；也包含"已到目的站、等快递员取件"和"派送失败、待再派" | 否 |
| DELIVERING | 派送中（快递员已取件） | 否 |
| RECEIVED | 已签收（用户自助签收或快递员妥投） | 是 |
| REFUSED | 已拒收，同时生成退回单 | 是 |
| CANCELLED | 已取消（仅首次发运前） | 是 |

**取消规则**：仅 `WAITING`、`AWAITING_RECEIPT`、`APPROVED` 且 `order_route` 中没有任何 `depart_time` 时可取消。进入 `SENDING` 后（已首次发运）不可取消，返回明确 409。

**拒收不是"退回中"**：原单在拒收那一刻结束，退回由一张方向相反的新订单承载；退回单从 `WAITING` 开始走完整流程。

**重复完成**：用户签收/拒收与快递员妥投/失败都使用 `state = 'DELIVERING'` 条件更新。任一端先完成即生效，另一端返回 409，不会重复签收或重复生成退回单。

---

## 5. 用户端（无需登录）

### 5.1 驿站列表

```
GET /api/station/list
```

响应 `data`：包含 `station.address`，以及轨迹地图使用的 WGS84 经纬度。
`latitude` / `longitude` 可能为 `null`（未维护坐标的历史数据），前端此时不画点。

```json
[{ "id": 1, "code": "ST001", "name": "北京转运中心",
   "address": "北京市朝阳区京通大道 18 号",
   "latitude": 39.9068844, "longitude": 116.4845489 }, ...]
```

坐标系是 WGS84（EPSG:4326），与 Leaflet + OpenStreetMap 底图一致；不是 GCJ-02 / BD-09。
坐标只用于地图展示，不修改 `address`，也不代表门牌级定位（8 站坐标依据见 `sql/seed.sql` 注释）。

### 5.2 发起订单

```
POST /api/user/orders
```

请求（只收用户可填字段，路线与状态由服务器生成；`weight`、`volumeCm3` 可选）：

```json
{ "senderName": "张三", "senderPhone": "13700000001",
  "receiverName": "李四", "receiverPhone": "13700000002",
  "fromStationId": 1, "toStationId": 6,
  "goodsDesc": "教材", "weight": 1.5, "volumeCm3": 12000 }
```

质量和体积都大于 0 时，按最短路写入预估价。响应：

```json
{ "code": 0, "message": "ok",
  "data": { "orderNo": "LD20261116C176D09BA303",
            "state": "WAITING",
            "approveDeadline": "2026-11-16T11:20:00",
            "volumeCm3": 12000, "quotedPrice": 33, "priceConfirmed": false } }
```

单号规则：`LD` + `yyyyMMdd` + UUID 前 12 位，共 22 字符。

### 5.3 按手机号查订单

```
GET /api/user/orders?phone=13700000001
```

同时匹配寄件人与收件人。响应：

```json
[{ "orderNo": "LD2026...", "state": "SENDING",
   "fromStation": "北京转运中心", "toStation": "上海转运中心",
   "createTime": "2026-11-16T11:18:00",
   "quotedPrice": 33, "priceConfirmed": false }]
```

用户首页同时支持按运单号查件：前端用 `GET /api/user/orders/{orderNo}` 校验后跳转详情。

### 5.4 订单详情

```
GET /api/user/orders/{orderNo}
```

响应：

```json
{ "orderNo": "LD2026...", "state": "SENDING",
  "senderName": "张三", "senderPhone": "13700000001",
  "receiverName": "李四", "receiverPhone": "13700000002",
  "fromStation": { "id": 1, "code": "ST001", "name": "北京转运中心", "address": "...",
                   "latitude": 39.9068844, "longitude": 116.4845489 },
  "toStation":   { "id": 6, "code": "ST006", "name": "上海转运中心", "address": "...",
                   "latitude": 31.2067567, "longitude": 121.2584826 },
  "currentStation": { "id": 1, "code": "ST001", "name": "北京转运中心", "address": "...",
                      "latitude": 39.9068844, "longitude": 116.4845489 },
  "currentPosition": {
    "mode": "IN_TRANSIT",
    "label": "运输中：北京转运中心 → 天津转运中心",
    "seq": 1,
    "stationId": 1, "stationName": "北京转运中心",
    "latitude": 39.5573380, "longitude": 116.8100624,
    "fromStationId": 1, "fromStationName": "北京转运中心",
    "toStationId": 2, "toStationName": "天津转运中心"
  },
  "estimatedDays": 4,
  "estimatedDeliveryAt": "2026-11-20T11:18:00",
  "goodsDesc": "教材", "weight": 1.5, "volumeCm3": 12000,
  "quotedPrice": 33, "price": 33, "priceConfirmed": true,
  "trajectory": [
    { "seq": 1, "stationId": 1, "stationName": "北京转运中心",
      "latitude": 39.9068844, "longitude": 116.4845489, "status": "ARRIVED",
      "departTime": "2026-11-16T11:19:00", "arriveTime": "2026-11-16T11:18:00" },
    { "seq": 2, "stationId": 2, "stationName": "天津转运中心",
      "latitude": 39.2077916, "longitude": 117.1355758, "status": "PENDING",
      "departTime": null, "arriveTime": null, "tripNo": "TR001" }
  ],
  "events": { "createdAt": "...", "approvedAt": "...",
              "originReceivedAt": "...", "receivedAt": null,
              "refusedAt": null, "cancelledAt": null },
  "returnOrderNo": null, "originalOrderNo": null }
```

- `estimatedDays`：按 2.6 的规则；批准后按固化路线计算。
- `estimatedDeliveryAt`：下单时间 + `estimatedDays` 天。
- `trajectory[].status`：`PENDING` / `ARRIVED` / `DELIVERING` / `SIGNED` / `REFUSED`
- `trajectory[].stationId` / `latitude` / `longitude`：该节点驿站及 WGS84 坐标，前端据此画折线。
- `currentPosition`：服务器按订单状态和 `order_route` 的真实到发时间算好的当前位置，
  查件页和订单详情共用，前端不自行猜状态。可能为 `null`（无坐标的历史数据）。
- `returnOrderNo`：该单被拒收后，指向生成的退回单。
- `originalOrderNo`：本单若是退回单，指向原单。

#### 5.4.1 当前位置模式

| `mode` | 含义 | 标记位置 |
|---|---|---|
| `NOT_SHIPPED` | 待审批 / 已拒绝 / 已取消，尚未进入运输网 | 始发站 |
| `AT_STATION` | 已批准待收件/待发运，或运输途中到站待发 | 当前站 |
| `IN_TRANSIT` | 已从 `fromStation` 发运、`toStation` 尚未到货 | 两站经纬度中点 |
| `DELIVERING` | 目的站最后一公里派送中 | 目的站 |
| `RECEIVED` | 已签收 | 最后已知站（正常为目的站） |
| `REFUSED` | 已拒收 | 最后已知站（正常为目的站） |

判定依据是 `order_route.actual_arrive_time` / `depart_time` 与 `current_seq`：
`current_seq` 那一站已写 `depart_time`、下一站还没写 `actual_arrive_time` 时才算 `IN_TRANSIT`；
其余 `SENDING` 情况都落在站上。`DELIVERING` / `RECEIVED` / `REFUSED` 不会再显示为移动中。
这是站点/路段级示意，不是车辆实时 GPS。

### 5.5 审批状态轮询

```
GET /api/user/orders/{orderNo}/status
```

```json
{ "orderNo": "LD2026...", "state": "WAITING",
  "approveDeadline": "2026-11-16T11:20:00", "remainingSeconds": 58 }
```

建议前端每 2 秒轮询一次，直到 `state` 变为 `AWAITING_RECEIPT` 或 `REJECTED`。

### 5.6 二维码内容

```
GET /api/user/orders/{orderNo}/qrcode
```

```json
{ "orderNo": "LD2026...", "content": "LD2026...", "type": "ORDER_NO" }
```

服务器只给出字符串；用户订单详情由前端用 `qrcode` 库画成二维码图片。

### 5.7 取消未发运订单

```
POST /api/user/orders/{orderNo}/cancel
```

请求：

```json
{ "senderPhone": "13700000001" }
```

前置条件：

- 寄件人手机号与订单一致，否则 403；
- 状态为 `WAITING`、`AWAITING_RECEIPT` 或 `APPROVED`（即首次发运前），否则 409；
- `order_route` 中不存在已写出发时间的路段（服务端再次校验）。

成功返回 `{ "code": 0 }`，订单转 `CANCELLED` 并在详情的 `events.cancelledAt` 中记录取消时间。退回单若尚未发运，规则完全相同，用退回单自己的寄件人手机号取消。

### 5.8 签收

```
POST /api/user/orders/{orderNo}/sign
```

```json
{ "receiverPhone": "13700000002" }
```

前置条件：状态必须是 `DELIVERING`，且手机号与收件人一致。否则：

```
403 手机号与收件人不一致
409 当前状态不可签收：SENDING
409 签收失败，订单状态已变化或已被快递员妥投
```

### 5.9 拒收

```
POST /api/user/orders/{orderNo}/refuse
```

```json
{ "receiverPhone": "13700000002", "reason": "包装破损" }
```

原单转为 `REFUSED`，同时生成退回单并返回：

```json
{ "orderNo": "LD202611161D3D43C56494", "state": "WAITING",
  "approveDeadline": "2026-11-16T11:20:00" }
```

退回单的寄件人是原收件人、收件人是原寄件人，起止站对调，货物信息照抄。退回单不沿用原单最终运费，批准后在新的始发站（原目的站）收件时重新核价。

### 5.10 修改重提

```
PUT /api/user/orders/{orderNo}
```

请求体同 5.2。仅 `REJECTED` 状态可调用，最多 3 次（`logistics.max-resubmit`）。
重提后状态回到 `WAITING`，`reject_reason` 清空，审批截止时间重置。

---

## 6. 管理端（需登录）

### 6.1 接口与权限矩阵

| 接口 | 路径管理员 | 节点管理员 | 说明 |
|---|---|---|---|
| `GET /api/admin/orders/pending` | ✔ | ✘ | 待审批列表 |
| `GET /api/admin/orders/inbox` | ✘ | ✔ 本站 | 本站待办（收件/发运/到货/派单） |
| `GET /api/admin/orders/{no}` | ✔ | ✔ | 订单详情 |
| `GET /api/admin/orders/{no}/plan` | ✔ | ✘ | 推荐路线 |
| `POST /api/admin/orders/{no}/approve` | ✔ | ✘ | 批准 |
| `POST /api/admin/orders/{no}/reject` | ✔ | ✘ | 拒绝 |
| `POST /api/admin/orders/{no}/receive` | ✘ | ✔ 始发站 | 确认收件并核价 |
| `POST /api/admin/orders/{no}/trip` | ✘ | ✔ 出发站 | 登记车次 |
| `POST /api/admin/orders/{no}/depart` | ✘ | ✔ 出发站 | 发运 |
| `POST /api/admin/orders/{no}/arrive` | ✘ | ✔ 到达站 | 到货 |
| `POST /api/admin/orders/{no}/courier` | ✘ | ✔ 目的站 | 最后一公里派单 |
| `GET /api/admin/postmen` | ✔ 全部 | ✔ 仅本站 | 快递员列表 |
| `GET /api/admin/postmen/{id}/tasks` | ✔ | ✔ 仅本站 | 快递员手里的单 |

"出发站/到达站/始发站/目的站"指操作人必须属于对应的驿站（`operator.station_id`）。

### 6.2 待审批列表

```
GET /api/admin/orders/pending
```

响应同 5.3 的结构（`state` 均为 `WAITING`）。

### 6.3 推荐路线

```
GET /api/admin/orders/{orderNo}/plan
```

```json
{ "stations": [ { "id": 1, "code": "ST001", "name": "北京转运中心", "address": "...",
                  "latitude": 39.9068844, "longitude": 116.4845489 }, ... ],
  "totalKm": 1377.0 }
```

用 Dijkstra 在驿站无向图上求最短路径。

### 6.4 批准

```
POST /api/admin/orders/{orderNo}/approve
```

```json
{ "stationIds": [1, 4, 7, 5, 6] }
```

`stationIds` 可省略，省略时使用最短路径。传了就必须满足：首站=始发站、末站=目的站、不重复、相邻两站之间有线路。

批准后固定 `order_route`（每个驿站一行，`seq` 从 1 开始），订单置为 **`AWAITING_RECEIPT`**。此时始发站那一行仍是 `PENDING`，不能登记车次或发运，必须先由始发站确认收件。

### 6.5 始发站确认收件并核价

```
POST /api/admin/orders/{orderNo}/receive      // 节点管理员
POST /api/postman/orders/{orderNo}/receive    // 本站快递员，见 7.5
```

请求（字段可省略，省略时使用用户申报值；申报值也不完整则 400）：

```json
{ "weightKg": 1.5, "volumeCm3": 12000 }
```

前置条件：

- 订单状态为 `AWAITING_RECEIPT`；
- 操作人属于始发站（节点管理员或本站快递员）。

同一事务内写入：`receipt_confirmed_at`、实测体积/质量、最终运费、`price_confirmed_at`、核价人；订单转 `APPROVED`；始发站路段置为 `ARRIVED`。重复确认返回 409。响应为运费结果（同 2.7，带 `orderNo`、`priceConfirmed=true`、`priceConfirmedAt`、`estimatedDays`）。

### 6.6 拒绝

```
POST /api/admin/orders/{orderNo}/reject
{ "reason": "货物信息不完整" }
```

### 6.7 登记车次（中转段）

```
POST /api/admin/orders/{orderNo}/trip
{ "tripNo": "TR001" }
```

为**下一段**登记承运车次。中转运输由车次承运，不涉及快递员。只有始发站确认收件后（`APPROVED` / `SENDING`）才能登记。

### 6.8 发运 / 到货

```
POST /api/admin/orders/{orderNo}/depart   // 出发站：确认由该车次带走
POST /api/admin/orders/{orderNo}/arrive   // 到达站：确认到货
```

`depart` 要求：

- 订单处于 `APPROVED` 或 `SENDING`；
- **最终运费已确认**，否则 409「请先在始发站确认收件并核价，再发运」；
- 下一段已登记车次，否则 400「请先为下一段指派车次」。

到货后订单**仍是 `SENDING`**，要等快递员取件才转 `DELIVERING`。

### 6.9 本站待办

```
GET /api/admin/orders/inbox
```

响应 `data` 是 `StationInboxItem[]`，`bucket` 取：

- `RECEIVE`：已批准、待本站确认收件并核价；
- `DEPART`：已收件核价、待本站登记车次并发运；
- `ARRIVE`：下一段到本站、待确认到货；
- `DISPATCH`：已到目的站、待派快递员（含派送失败退回待再派）。

条目带 `weight`、`volumeCm3`、`priceConfirmed`、`remark`（如「派送失败：联系不上」），供页面直接展示。

### 6.10 最后一公里派单

```
POST /api/admin/orders/{orderNo}/courier
{ "postmanId": 11 }
```

前置条件：

- 订单已到达目的站（`current_seq == forward_station_count`）；
- 操作人是目的站管理员；
- 快递员属于目的站，否则 `400 最后一公里须由目的站（上海转运中心）的快递员承运`。

派送失败后订单回到目的站待派，`postman_id` 与取件时间被清空；站点可以重新派给同一人或其他人。

### 6.11 快递员列表

```
GET /api/admin/postmen?stationId=1
```

节点管理员传的 `stationId` 会被强制改为自己所属站。

```json
[{ "id": 1, "username": "pm001", "name": "何京生", "phone": "13900000001",
   "stationId": 1, "stationName": "北京转运中心", "status": 1,
   "activeTasks": 1, "doneTasks": 0 }]
```

### 6.12 快递员手里的单

```
GET /api/admin/postmen/{postmanId}/tasks?type=pending
GET /api/admin/postmen/{postmanId}/tasks?type=completed
```

响应结构见 7.2。管理端这里只统计最后一公里任务，始发收件待办在本站 `inbox` 与快递员自己的任务列表中。

---

## 7. 快递员端（需登录）

### 7.1 我的任务

```
GET /api/postman/orders?type=pending
```

- `pending`（默认）：
  - `taskType = RECEIVE`：本站始发、等待确认收件并核价的单（`AWAITING_RECEIPT`）；
  - `taskType = DELIVERY`：`SENDING`（货已到站等取件）+ `DELIVERING`（派送中）。
- `completed`：`RECEIVED` + `REFUSED`。

### 7.2 任务项结构

```json
{ "orderNo": "LD2026...", "orderState": "SENDING",
  "seq": 5, "forwardStationCount": 5,
  "fromStationName": "上海转运中心",
  "toStationName": "收件人 李四",
  "tripNo": null, "departTime": null, "arriveTime": "2026-11-16T11:14:00",
  "legStatus": "ARRIVED", "lastMile": true,
  "taskType": "DELIVERY", "remark": null,
  "senderName": "张三", "receiverName": "李四",
  "receiverPhone": "13700000002", "goodsDesc": "教材",
  "weight": 1.5, "volumeCm3": 12000, "quotedPrice": 33,
  "price": 33, "priceConfirmed": true }
```

- `taskType = RECEIVE` 时 `seq = 1`，`toStationName` 是目的站，需先确认收件核价。
- `lastMile` 由 `seq == forwardStationCount` 推导，不存库。
- `remark` 保存最近一次派送失败原因等备注。

### 7.3 按单号定位任务（手工录入 / 扫码）

```
GET /api/postman/orders/{orderNo}
```

只返回本人的待办任务：

- 最后一公里任务（货物已到目的站待取或派送中）；
- 或本站始发待收件任务。

越权访问别人的单返回 403；已完成/当前不可操作返回 409；订单不存在返回 404。前端不强制使用摄像头，输入单号即可。

### 7.4 核对运费（只读复核）

```
POST /api/postman/orders/{orderNo}/price
```

最终核价已前移到始发站收件；本接口不再改价，只按已保存的实测体积/质量和固化路线重算并返回同一结果。请求体可省略。若该单尚未核价则返回 409，避免目的站出现第二个最终价入口。

### 7.5 始发站快递员确认收件

```
POST /api/postman/orders/{orderNo}/receive
```

请求同 6.5。只能操作本站始发、状态为 `AWAITING_RECEIPT` 的订单；核价人记为该快递员。

### 7.6 取件开始派送

```
POST /api/postman/orders/{orderNo}/pickup
```

前置条件：状态 `SENDING`、货已到目的站、该单指派给本人、路段已到站，且**运费已在始发站确认**。请求体可省略；不再在取件时核价。

成功：订单转 `DELIVERING`，同时记录取件时间。之后由收件人签收/拒收，或由快递员妥投/登记失败。

### 7.7 妥投 / 派送失败

```
POST /api/postman/orders/{orderNo}/deliver
```

妥投：

```json
{ "outcome": "DELIVERED", "method": "SELF" }
```

- `method = SELF`：本人签收；`method = PHONE`：电话确认。
- 订单转 `RECEIVED`，末段路段转 `SIGNED`。与用户端签收二选一，重复完成返回 409。

派送失败：

```json
{ "outcome": "FAILED", "reason": "UNREACHABLE" }
```

- `reason = UNREACHABLE`：联系不上；`reason = WRONG_ADDRESS`：地址有误。
- 订单从 `DELIVERING` 回到 `SENDING`；末段路段回到 `ARRIVED`，清空 `depart_time` 与 `postman_id`，备注失败原因。
- 订单重新出现在目的站 `DISPATCH` 待办，站点可再派给同一快递员或其他人，不会卡死。
- 失败不会生成退回单；正式拒收仍由收件人走 5.9。

---

## 8. 完整流程

```
用户下单            POST /user/orders                        → WAITING
用户取消（可选）    POST /user/orders/{no}/cancel             → CANCELLED（仅首次发运前）
路径管理员审批      POST /admin/orders/{no}/approve           → AWAITING_RECEIPT（固化路线）
始发站确认收件      POST /admin/orders/{no}/receive           → APPROVED（同时核价，锁定最终运费）
                   或 POST /postman/orders/{no}/receive
起点站登记车次      POST /admin/orders/{no}/trip               {tripNo: TR001}
起点站发运          POST /admin/orders/{no}/depart             → SENDING
下一站确认到货      POST /admin/orders/{no}/arrive             （循环 4 次，每次先登记车次）
目的站派快递员      POST /admin/orders/{no}/courier            {postmanId: 11}
快递员取件          POST /postman/orders/{no}/pickup           → DELIVERING
收件人签收          POST /user/orders/{no}/sign                → RECEIVED
                   或快递员妥投 POST /postman/orders/{no}/deliver {outcome: DELIVERED}
```

派送失败分支：

```
快递员登记失败      POST /postman/orders/{no}/deliver {outcome: FAILED}  → SENDING（待再派）
目的站重新派单      POST /admin/orders/{no}/courier
```

拒收分支：

```
收件人拒收          POST /user/orders/{no}/refuse            → 原单 REFUSED
                                                             + 新退回单 WAITING
退回单从 WAITING 重新走审批、始发收件核价、运输、派送流程，收件人是原寄件人
```

---

## 9. 数据库脚本

| 文件 | 作用 |
|---|---|
| `sql/schema.sql` | 建库建表，含 CHECK 约束与索引 |
| `sql/seed.sql` | 8 个驿站、10 条线路、9 个操作员、16 名快递员 |
| `sql/alter_pricing.sql` | 旧库增量补充运费字段 |
| `sql/alter_final_six.sql` | 旧库增量补充取消/收件核价列与状态 CHECK |
| `sql/alter_track_map.sql` | 旧库增量补充驿站 WGS84 经纬度，并写入 8 站坐标 |
| `sql/test.sql` | 43 条断言，覆盖驿站坐标与完整生命周期（事务内执行，结束回滚） |

```bash
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 应输出「43 条用例全部通过」
```

---

## 10. 已知限制

1. 会话存在内存里，重启即失效；多实例部署需要集中式 Session。
2. 口令用 SHA-256 + 盐，无工作因子；生产环境应换 BCrypt/Argon2。
3. 二维码不防伪造，任何人拿到单号都能查到订单详情（与"凭单号查询"的业务设定一致）。
4. 退回单被管理员拒绝时，货物的线下处置不在系统范围内。
5. 列表接口未分页，数据量上去后需要补。
6. 预计时效按"每跨一站 1 日"的规则估算，不是真实路由预测，也不含节假日/天气等因素。
7. 派送失败只记录原因并回到目的站待派，不提供改址、转寄、二次上门预约等能力。
8. 网点查询只展示站名和地址，不提供地图导航。
9. 轨迹地图使用 OpenStreetMap 公共瓦片和 Leaflet，需要能访问 `tile.openstreetmap.org`；
   离线/内网环境底图无法加载。生产部署请遵守 OSM 瓦片使用政策（或换自建瓦片），并保留署名。
10. 当前位置是站点/路段级示意，不是车辆实时 GPS；在途只标在相邻两站中点。
    8 个站中 7 个因公开门牌数据不足只能精确到道路/路口，ST008（文一西路 969 号）为门牌级，
    依据见 `sql/seed.sql` 与 `sql/alter_track_map.sql` 注释。
