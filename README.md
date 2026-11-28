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
init.py          Python 3 一键初始化（检查环境、准备 MySQL、初始化/升级库、安装依赖、按需构建）
init.cmd         Windows 双击初始化入口
start.cmd        Windows 双击启动入口
start.ps1        Windows PowerShell 一键启动（启动后端并打开浏览器）
sql/
  schema.sql     建库建表、约束、索引
  seed.sql       8 个驿站、10 条线路、9 个操作员、16 名快递员
  test.sql       44 条结构/生命周期断言（事务内执行，结束回滚）
  alter_*.sql    旧库增量升级脚本（含 alter_track_map.sql / alter_track_map_gcj02.sql 驿站 GCJ-02 经纬度）
docs/API.md      接口文档
web/             前端工程（见 web/README.md，高德 Key 放在 web/.env.local）
```

## 快速开始

初始化只准备环境、数据库和前端，不启动后端；启动脚本负责拉起 Spring Boot 并打开浏览器。
两步都会跳过已完成的步骤，可以安全地重复运行。

### 第 1 步：一键初始化（Python 3.9+）

在仓库根目录执行：

```bash
python init.py
```

macOS / Linux 若没有 `python` 命令，用 `python3 init.py`。Windows 也可以直接双击 `init.cmd`
（内部优先 `py -3`，没有则回退 `python`）。`init.py` 只用 Python 标准库，不需要 pip 安装依赖。

`init.py` 会依次：

- 检查 Python 3.9+、JDK 21+、Node.js 18+ / npm。Windows 上缺少 JDK 或 Node.js 时，会尝试用
  `winget` 非交互安装 Microsoft OpenJDK 21 / Eclipse Temurin 21 和 Node.js LTS；winget 失败或
  非 Windows 时，打印人工安装步骤并停止。
- 准备 MySQL 8（`127.0.0.1:3308`，库 `logistics`，root / 123456）。有 Docker 时，若 3308 未就绪
  则创建或启动 `logistics-mysql` 容器（`3308:3306`，utf8mb4）；已有同名容器只启动，不删除重建。
  没有 Docker 时，需要本机已有监听 3308 的 MySQL 8。
- 检查数据库：只有 `logistics` 库不存在，或六张关键表一张都不存在时才执行 `sql/schema.sql` +
  `sql/seed.sql`；关键表只存在一部分时不会自动重建，会停止并打印需要手动执行的脚本。
  已有库只做结构检查，按需执行可重复的增量脚本（`sql/alter_pricing.sql`、`sql/alter_final_six.sql`、
  `sql/alter_track_map.sql`，必要时 `sql/alter_track_map_gcj02.sql`），不会 DROP 已有业务表。
- 在 `web/` 执行 `npm install`（依赖已存在时跳过）。`web/.env.local` 不存在时从 `web/.env.example`
  复制，已有 `.env.local` 绝不覆盖。`src/main/resources/static/index.html` 不存在时执行
  `npm run build`，已有产物不重复构建。
- 最后打印 Java / Node.js / MySQL / 数据库 / 前端依赖 / 静态产物 / 高德 Key 的就绪摘要；
  Key 只显示「已填写」或「尚未填写」，不显示内容。

> `init.py` 不会启动 Spring Boot，也不会打开浏览器。重复运行会跳过已完成的步骤。
> 只有显式执行 `python init.py --reset-db` 才会删除 `logistics` 的业务表并重新执行
> `schema.sql` + `seed.sql`。该参数会清空现有业务数据，使用前务必备份。

### 第 2 步：一键启动（Windows）

双击 `start.cmd`，或在 PowerShell 中执行：

```powershell
.\start.ps1
```

需要额外启动 Vite 开发服务器（http://127.0.0.1:5173/）时用 `.\start.ps1 -Dev`。

启动脚本会再次检查 Java / Node.js / MySQL，按需补齐数据库和前端（已完成的步骤直接跳过），
然后在新窗口运行 `mvnw.cmd spring-boot:run`，等待 18080 就绪后打开 http://127.0.0.1:18080/。

- **不会覆盖已有数据库**：数据库已存在时只做检查和可重复的增量升级，不执行 `schema.sql` 的 DROP；
  只有 `init.py --reset-db` 会清空重建。
- **不会覆盖已有 `.env.local`**：只在文件不存在时从 `.env.example` 复制，不会写入或修改高德 Key。
- **不会把 Key 写入仓库**：高德 Key 只保存在被 `.gitignore` 忽略的 `web/.env.local` 中；
  `src/main/resources/static/` 是构建产物，同样不会提交。
- 未填写 `VITE_AMAP_JS_API_KEY` 时系统仍可启动，但轨迹地图会提示缺少 Key。填写后重新运行
  `start.ps1` 即可：它会发现 `.env.local` 比构建产物新并自动重建；也可以手动执行
  `cd web && npm run build`。
- 已有前端构建产物时不会每次重建。需要强制重建时，删除 `src/main/resources/static/` 后重新运行
  `init.py` / `start.ps1`，或在 `web/` 执行 `npm run build`。

`init.cmd` / `start.cmd` 适用于 Windows（PowerShell 5.1 与 PowerShell 7）。`init.py` 本身也可在
macOS / Linux 上运行，但 `start.cmd` / `start.ps1` 仅支持 Windows；非 Windows 请按下面的手动步骤启动。

### 手动方式（不使用一键脚本）

一键脚本本质上也是执行下面这些步骤；需要排查问题或在 macOS / Linux 上运行时可以手动操作。

#### 数据库

```bash
mysql -h127.0.0.1 -P3308 -uroot -p < sql/schema.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/seed.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 可选：应输出「44 条用例全部通过」
```

`schema.sql` 会 DROP 并重建业务表，只适合首次初始化或确认数据可丢弃时执行。
已有旧库不想重建时，按顺序执行 `sql/alter_pricing.sql`、`sql/alter_final_six.sql`、
`sql/alter_track_map.sql` 增量升级。若该库此前已经执行过旧版 `alter_track_map.sql`
（库内是 WGS84 坐标），再执行 `sql/alter_track_map_gcj02.sql` 原地转换为 GCJ-02；
两个脚本都可以重复执行。

#### 构建前端

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

#### 启动后端

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
# Windows 用 mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

默认端口 8080。若环境里存在 `SERVER_PORT`（部分 IDE / 工具链会注入），会覆盖默认值并可能撞端口，
所以上面显式指定 18080。启动后访问 http://127.0.0.1:18080/ 。

#### 打包

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
