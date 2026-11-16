# 物流配送管理系统 · 接口文档

> 版本：v1（2026-11-16）
> 后端：Spring Boot 4.1.2 + MyBatis 4.1.0 + MySQL 8（Docker，宿主机 3308）

---

## 1. 概述

### 1.1 启动

```bash
# 数据库（首次）
mysql -h127.0.0.1 -P3308 -uroot -p < sql/schema.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/seed.sql

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
| `STATION_ADMIN` | 节点管理员：管本站的车次、发运、到货、派单 | `/auth/operator/login` |
| `POSTMAN` | 快递员：只负责最后一公里 | `/auth/postman/login` |

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
| 400 | 参数不合法 | 手机号格式错、车次为空、起止站相同 |
| 401 | 未登录 | 未携带会话访问管理端/快递员端 |
| 403 | 无权操作 | 角色不符、跨站操作、快递员扫了别人的单 |
| 404 | 资源不存在 | 订单号或快递员不存在 |
| 409 | 状态冲突 | 重复签收、未到站就取件、状态已被并发修改 |
| 500 | 服务端异常 | 未预期的错误 |

### 2.3 鉴权

管理端与快递员端使用 **Session**（`JSESSIONID` Cookie）：

1. 调登录接口，服务端返回 `Set-Cookie`；
2. 之后每个请求带上该 Cookie；
3. `/api/admin/**` 仅管理员可访问，`/api/postman/**` 仅快递员可访问；
4. 未登录返回 `401`，角色不符返回 `403`。

用户端、驿站列表、登录接口公开，不需要 Cookie。

### 2.4 其他

- 时间格式为 ISO-8601，如 `2026-11-16T11:15:51`。
- 枚举以字符串返回，如 `state = "DELIVERING"`。
- 所有金额/重量用小数，`weight` 单位为千克。

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
WAITING ──批准──→ APPROVED ──起点站发运──→ SENDING ──快递员取件──→ DELIVERING
   │                                          │                      ├──签收──→ RECEIVED
   ├──拒绝/超时──→ REJECTED ──重提──→ WAITING  │                      └──拒收──→ REFUSED
   │                                          └──逐站到货（状态保持 SENDING）
```

| 状态 | 含义 | 终态 |
|---|---|---|
| WAITING | 待审批 | 否 |
| APPROVED | 已批准，等待发运 | 否 |
| REJECTED | 已拒绝（管理员拒绝或超时），可修改重提 | 否 |
| SENDING | 运送中；也包含"已到目的站、等快递员取件" | 否 |
| DELIVERING | 派送中（快递员已取件） | 否 |
| RECEIVED | 已签收 | 是 |
| REFUSED | 已拒收，同时生成退回单 | 是 |

**拒收不是"退回中"**：原单在拒收那一刻结束，退回由一张方向相反的新订单承载。

---

## 5. 用户端（无需登录）

### 5.1 驿站列表

```
GET /api/station/list
```

响应 `data`：`[{ "id": 1, "code": "ST001", "name": "北京转运中心" }, ...]`

### 5.2 发起订单

```
POST /api/user/orders
```

请求（只收这 7 个字段，路线与状态由服务器生成）：

```json
{ "senderName": "张三", "senderPhone": "13700000001",
  "receiverName": "李四", "receiverPhone": "13700000002",
  "fromStationId": 1, "toStationId": 6,
  "goodsDesc": "教材", "weight": 1.5 }
```

响应：

```json
{ "code": 0, "message": "ok",
  "data": { "orderNo": "LD20261116C176D09BA303",
            "state": "WAITING",
            "approveDeadline": "2026-11-16T11:20:00" } }
```

单号规则：`LD` + `yyyyMMdd` + UUID 前 12 位，共 22 字符。

### 5.3 按手机号查订单号

```
GET /api/user/orders?phone=13700000001
```

同时匹配寄件人与收件人。响应：

```json
[{ "orderNo": "LD2026...", "state": "SENDING",
   "fromStation": "北京转运中心", "toStation": "上海转运中心",
   "createTime": "2026-11-16T11:18:00" }]
```

### 5.4 订单详情

```
GET /api/user/orders/{orderNo}
```

响应：

```json
{ "orderNo": "LD2026...", "state": "SENDING",
  "senderName": "张三", "senderPhone": "13700000001",
  "receiverName": "李四", "receiverPhone": "13700000002",
  "fromStation": { "id": 1, "code": "ST001", "name": "北京转运中心" },
  "toStation":   { "id": 6, "code": "ST006", "name": "上海转运中心" },
  "currentStation": { "id": 5, "code": "ST005", "name": "南京转运中心" },
  "goodsDesc": "教材", "weight": 1.5,
  "trajectory": [
    { "seq": 1, "stationName": "北京转运中心", "status": "ARRIVED",
      "departTime": "2026-11-16T11:19:00", "arriveTime": "2026-11-16T11:18:00" },
    { "seq": 2, "stationName": "天津转运中心", "status": "ARRIVED",
      "departTime": "2026-11-16T11:20:00", "arriveTime": "2026-11-16T11:19:30",
      "tripNo": "TR001" }
  ],
  "events": { "createdAt": "...", "approvedAt": "...",
              "receivedAt": null, "refusedAt": null },
  "returnOrderNo": null }
```

- `trajectory[].status`：`PENDING` / `ARRIVED` / `DELIVERING` / `SIGNED` / `REFUSED`
- `returnOrderNo`：该单被拒收后，指向生成的退回单

### 5.5 审批状态轮询

```
GET /api/user/orders/{orderNo}/status
```

```json
{ "orderNo": "LD2026...", "state": "WAITING",
  "approveDeadline": "2026-11-16T11:20:00", "remainingSeconds": 58 }
```

建议前端每 2 秒轮询一次，直到 `state` 变为 `APPROVED` 或 `REJECTED`。

### 5.6 二维码内容

```
GET /api/user/orders/{orderNo}/qrcode
```

```json
{ "orderNo": "LD2026...", "content": "LD2026...", "type": "ORDER_NO" }
```

服务器只给出字符串，图片由前端用 qrcode.js / 小程序 canvas 生成。

### 5.7 签收

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
```

### 5.8 拒收

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

退回单的寄件人是原收件人、收件人是原寄件人，起止站对调，货物信息照抄。

### 5.9 修改重提

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
| `GET /api/admin/orders/{no}/plan` | ✔ | ✘ | 推荐路线 |
| `POST /api/admin/orders/{no}/approve` | ✔ | ✘ | 批准 |
| `POST /api/admin/orders/{no}/reject` | ✔ | ✘ | 拒绝 |
| `POST /api/admin/orders/{no}/trip` | ✘ | ✔ 出发站 | 登记车次 |
| `POST /api/admin/orders/{no}/depart` | ✘ | ✔ 出发站 | 发运 |
| `POST /api/admin/orders/{no}/arrive` | ✘ | ✔ 到达站 | 到货 |
| `POST /api/admin/orders/{no}/courier` | ✘ | ✔ 目的站 | 最后一公里派单 |
| `GET /api/admin/postmen` | ✔ 全部 | ✔ 仅本站 | 快递员列表 |
| `GET /api/admin/postmen/{id}/tasks` | ✔ | ✔ 仅本站 | 快递员手里的单 |

"出发站/到达站/目的站"指操作人必须属于对应的驿站（`operator.station_id`）。

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
{ "stations": [ { "id": 1, "code": "ST001", "name": "北京转运中心" }, ... ],
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

批准后固定 `order_route`（每个驿站一行，`seq` 从 1 开始），并把订单置为 `APPROVED`。

### 6.5 拒绝

```
POST /api/admin/orders/{orderNo}/reject
{ "reason": "货物信息不完整" }
```

### 6.6 登记车次（中转段）

```
POST /api/admin/orders/{orderNo}/trip
{ "tripNo": "TR001" }
```

为**下一段**登记承运车次。中转运输由车次承运，不涉及快递员。

### 6.7 发运 / 到货

```
POST /api/admin/orders/{orderNo}/depart   // 出发站：确认由该车次带走
POST /api/admin/orders/{orderNo}/arrive   // 到达站：确认到货
```

`depart` 要求下一段已登记车次，否则 `400 请先为下一段指派车次`。

到货后订单**仍是 `SENDING`**，要等快递员取件才转 `DELIVERING`。

### 6.8 最后一公里派单

```
POST /api/admin/orders/{orderNo}/courier
{ "postmanId": 11 }
```

前置条件：

- 订单已到达目的站（`current_seq == forward_station_count`）；
- 操作人是目的站管理员；
- 快递员属于目的站，否则 `400 最后一公里须由目的站（上海转运中心）的快递员承运`。

### 6.9 快递员列表

```
GET /api/admin/postmen?stationId=1
```

节点管理员传的 `stationId` 会被强制改为自己所属站。

```json
[{ "id": 1, "username": "pm001", "name": "何京生", "phone": "13900000001",
   "stationId": 1, "stationName": "北京转运中心", "status": 1,
   "activeTasks": 1, "doneTasks": 0 }]
```

### 6.10 快递员手里的单

```
GET /api/admin/postmen/{postmanId}/tasks?type=pending
GET /api/admin/postmen/{postmanId}/tasks?type=completed
```

响应结构见 7.2。

---

## 7. 快递员端（需登录）

### 7.1 我的任务

```
GET /api/postman/orders?type=pending
```

- `pending`（默认）：`SENDING`（货已到站等取件）+ `DELIVERING`（派送中）
- `completed`：`RECEIVED` + `REFUSED`

### 7.2 任务项结构

```json
{ "orderNo": "LD2026...", "orderState": "SENDING",
  "seq": 5, "forwardStationCount": 5,
  "fromStationName": "上海转运中心",
  "toStationName": "收件人 李四",
  "tripNo": null, "departTime": null, "arriveTime": "2026-11-16T11:14:00",
  "legStatus": "ARRIVED", "lastMile": true,
  "senderName": "张三", "receiverName": "李四",
  "receiverPhone": "13700000002", "goodsDesc": "教材" }
```

- `lastMile` 由 `seq == forwardStationCount` 推导，不存库。
- 快递员的 `toStationName` 是"收件人 XXX"，不是下一个驿站——他不跑干线。

### 7.3 取件开始派送

```
POST /api/postman/orders/{orderNo}/pickup
```

前置条件：状态 `SENDING`、货已到目的站、该单指派给本人、路段已到站。

成功：订单转 `DELIVERING`，同时记录取件时间。之后由收件人签收或拒收。

---

## 8. 完整流程

```
用户下单            POST /user/orders                     → WAITING
路径管理员审批      POST /admin/orders/{no}/approve        → APPROVED（固化 5 行路线）
起点站登记车次      POST /admin/orders/{no}/trip           {tripNo: TR001}
起点站发运          POST /admin/orders/{no}/depart         → SENDING
下一站确认到货      POST /admin/orders/{no}/arrive         （循环 4 次，每次先登记车次）
目的站派快递员      POST /admin/orders/{no}/courier        {postmanId: 11}
快递员取件          POST /postman/orders/{no}/pickup       → DELIVERING
收件人签收          POST /user/orders/{no}/sign            → RECEIVED
```

拒收分支：

```
收件人拒收          POST /user/orders/{no}/refuse          → 原单 REFUSED
                                                             + 新退回单 WAITING
退回单重复上面的审批/车次/派单/签收流程，收件人是原寄件人
```

---

## 9. 数据库脚本

| 文件 | 作用 |
|---|---|
| `sql/schema.sql` | 建库建表，含 CHECK 约束与索引 |
| `sql/seed.sql` | 8 个驿站、10 条线路、9 个操作员、16 名快递员 |
| `sql/test.sql` | 32 条断言，覆盖完整生命周期（事务内执行，结束回滚） |

```bash
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 应输出「32 条用例全部通过」
```

---

## 10. 已知限制

1. 会话存在内存里，重启即失效；多实例部署需要集中式 Session。
2. 口令用 SHA-256 + 盐，无工作因子；生产环境应换 BCrypt/Argon2。
3. 二维码不防伪造，任何人拿到单号都能查到订单详情（与"凭单号查询"的业务设定一致）。
4. 退回单被管理员拒绝时，货物的线下处置不在系统范围内。
