# 物流配送管理系统

校园 / 城市内的小件物流系统：用户下单，路径管理员审批并固化路线，始发站确认收件并完成最终核价，
各站按车次完成干线运输，最后一公里由目的站快递员派送，收件人凭手机号签收或拒收，
快递员也可以登记本人签收、电话确认或派送失败（回到目的站待再派）。拒收会生成一张方向相反的退回单，走同一套流程。

## 功能

| 角色 | 能力 |
|---|---|
| 用户（不登录） | 下单、按手机号查单、按运单号查件、订单详情与预计时效、轨迹地图（当前站/在途位置）、审批状态轮询、运单二维码、取消未发运订单、签收、拒收、修改重提；公开运费试算，下单可填体积得到预估价 |
| 路径管理员 `PATH_ADMIN` | 待审批列表、最短路线推荐、批准（可指定途经节点）、拒绝、查单（含轨迹地图） |
| 节点管理员 `STATION_ADMIN` | 本站待收件（确认收件并核价）、车次登记、发运、到货确认、目的站最后一公里派单、查本站快递员 |
| 快递员 `POSTMAN` | 始发站收件核价；查看分给自己的单、单号定位、取件、妥投（本人签收/电话确认）、登记派送失败、查看最终运费 |

各门户共用左右分栏布局：窄侧栏，主区左侧列表、右侧详情与操作；品牌名「通驿」。

前端已覆盖四个入口：用户寄件/查件/网点、路径管理台、驿站工作台、快递员工作台。

## 技术栈

- 后端：Spring Boot 4.1.2、MyBatis 4.1.0、Spring Session（内存）、Java 21
- 数据库：MySQL 8（Docker，宿主机端口 3308）
- 前端：Vue 3 + TypeScript + Vite，高德 JS API 2.0 轨迹地图（GCJ-02 坐标系），设计语言为浅灰 / 乳白纸感拟物
- 构建：Maven Wrapper + npm

## 目录结构

```
src/main/java/norym/logisticsdeliverysystem/
  auth/          Session 登录、角色、口令摘要
  controller/    用户端、管理端、快递员端控制器与 SPA 路由回落
  service/       业务层，含 RoutePlanner（Dijkstra）、ReceiptService（始发收件核价）
  mapper/        MyBatis 接口
  entity/ dto/   实体与出入参
src/main/resources/
  mapper/*.xml   SQL
  static/        前端构建产物（.gitignore，需自行构建）
sql/
  schema.sql     建库建表、约束、索引
  seed.sql       8 个驿站、10 条线路、9 个操作员、16 名快递员
  test.sql       44 条结构/生命周期断言（事务内执行，结束回滚）
  alter_*.sql    旧库增量升级脚本（含 alter_track_map.sql / alter_track_map_gcj02.sql 驿站 GCJ-02 经纬度）
docs/API.md      接口文档
web/             前端工程（见 web/README.md，高德 Key 放在 web/.env.local）
```

## 快速开始

### 1. 数据库

```bash
mysql -h127.0.0.1 -P3308 -uroot -p < sql/schema.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/seed.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 可选：应输出「44 条用例全部通过」
```

已有旧库不想重建时，按顺序执行 `sql/alter_pricing.sql`、`sql/alter_final_six.sql`、
`sql/alter_track_map.sql` 增量升级。若该库此前已经执行过旧版 `alter_track_map.sql`
（库内是 WGS84 坐标），再执行 `sql/alter_track_map_gcj02.sql` 原地转换为 GCJ-02；
两个脚本都可以重复执行。

### 2. 构建前端（必须）

高德地图 Key 不写入仓库。先在本机配置：

```bash
cd web
npm install
cp .env.example .env.local     # 然后把 VITE_AMAP_JS_API_KEY= 后面填上自己的 Web 端 JS API Key
npm run build                  # 产物写入 src/main/resources/static
```

`.env.local` 已被 `.gitignore` 忽略，不要提交。构建时也兼容进程环境变量
`AMAP_JS_API_KEY`（优先级低于 `.env.local` 的 `VITE_AMAP_JS_API_KEY`）。
未配置 Key 时地图区域会显示明确提示，不会使用任何写死的示例 Key。

> **`src/main/resources/static/` 是构建产物，没有入库，且会内嵌本机的高德 Key。**
> 从仓库拉下来后如果不执行这一步，后端只有接口，访问 `/` 是空的。
> 需要现场改前端时用 `npm run dev`（http://127.0.0.1:5173，`/api` 走代理）。
> 不要把构建产物提交进仓库，否则会把本地 Key 一起带进去。

### 3. 启动后端

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

默认端口 8080。若环境里存在 `SERVER_PORT`（部分 IDE / 工具链会注入），会覆盖默认值并可能撞端口，
所以上面显式指定 18080。启动后访问 http://127.0.0.1:18080/ 。

### 4. 打包

```bash
./mvnw package -DskipTests     # 先执行过 npm run build，页面才会进 jar
java -jar target/LogisticsDeliverySystem-*.jar --server.port=18080
```

## 演示账号

初始密码统一 `123456`，摘要为 `SHA-256(salt + 明文)`，`salt = MD5(username)` 前 16 位。

| 账号 | 角色 | 归属 |
|---|---|---|
| `admin` | 路径管理员 | — |
| `st001` ~ `st008` | 节点管理员 | ST001 ~ ST008 |
| `pm001` ~ `pm016` | 快递员 | 每站 2 人 |

## 验证

```bash
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 44 条数据库层断言

cd web
node scripts/flow-check.mjs     # 登录拦截 / 批准 / 拒绝 / 刷新保持会话
node scripts/shots.mjs          # 截图到 shots/（用本机 Edge，不下载浏览器）
```

## 文档

- 接口文档：[docs/API.md](docs/API.md)——通用约定、错误码、状态机、三端接口的请求响应、权限矩阵
- 前端说明：[web/README.md](web/README.md)——页面结构、开发与构建

## 已知限制

1. Session 存在内存，重启即失效；多实例部署需集中式 Session。
2. 口令为 SHA-256 + 盐，无工作因子；生产应换 BCrypt / Argon2。
3. 二维码不防伪造，拿到单号即可查询订单详情（与"凭单号查询"的业务设定一致）。
4. 退回单被管理员拒绝后，货物的线下处置不在系统范围内。
5. 列表接口未分页，数据量上去后需要补。
6. 预计送达按"每跨一站 1 日"估算，不接真实路由预测，也不考虑节假日/天气。
7. 派送失败只记录原因并回到目的站待再派，不做改址、转寄、二次预约。
8. 网点查询只展示站名和地址，不提供地图导航。
9. 轨迹地图使用高德 JS API 2.0 官方底图（GCJ-02 坐标系），需要能访问 `webapi.amap.com`
   及高德瓦片域名；Key 从 `web/.env.local` 的 `VITE_AMAP_JS_API_KEY` 读取（构建时也兼容
   `AMAP_JS_API_KEY`），未配置时地图区域会提示。Key 需在高德控制台配置当前域名白名单；
   离线/内网环境只能看到空白底图，但站点和当前位置标记逻辑不变。底图 logo 与版权信息由
   高德 JS API 默认控件保留，不要隐藏。
10. 当前位置是站点/路段级示意，不是车辆实时 GPS；在途取相邻两站 GCJ-02 坐标中点。
    8 个站中 7 个受公开门牌数据限制，坐标精确到道路/路口；ST008（文一西路 969 号）为门牌级。
    原 WGS84 坐标与转换后 GCJ-02 坐标都写在 `sql/seed.sql`、`sql/alter_track_map*.sql` 注释里。
