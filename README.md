# 物流配送管理系统

校园 / 城市内的小件物流系统：用户下单，路径管理员审批并固化路线，各站按车次完成干线运输，
最后一公里由目的站快递员派送，收件人扫码签收或拒收。拒收会生成一张方向相反的退回单，走同一套流程。

## 功能

| 角色 | 能力 |
|---|---|
| 用户（不登录） | 下单、按手机号查单、订单详情、审批状态轮询、二维码内容、签收、拒收、修改重提；公开运费试算，下单可填体积得到预估价 |
| 路径管理员 `PATH_ADMIN` | 待审批列表、最短路线推荐、批准（可指定途经节点）、拒绝 |
| 节点管理员 `STATION_ADMIN` | 本站车次登记、发运、到货确认、目的站最后一公里派单、查本站快递员 |
| 快递员 `POSTMAN` | 查看分给自己的单、核对运费、扫码取件开始派送 |

各门户共用墨底米色的左右分栏布局：窄侧栏，主区左侧列表、右侧详情与操作。

前端目前完成的是**路径管理台**（登录 / 待审批 / 快递员），另外两个角色的页面尚未开工。

## 技术栈

- 后端：Spring Boot 4.1.2、MyBatis 4.1.0、Spring Session（内存）、Java 25
- 数据库：MySQL 8（Docker，宿主机端口 3308）
- 前端：Vue 3 + TypeScript + Vite，设计语言为浅灰 / 乳白纸感拟物
- 构建：Maven Wrapper + npm

## 目录结构

```
src/main/java/norym/logisticsdeliverysystem/
  auth/          Session 登录、角色、口令摘要
  controller/    6 个控制器，25 个接口
  service/       业务层，含 RoutePlanner（Dijkstra）
  mapper/        MyBatis 接口
  entity/ dto/   实体与出入参
src/main/resources/
  mapper/*.xml   SQL
  static/        前端构建产物（.gitignore，需自行构建）
sql/
  schema.sql     建库建表、约束、索引
  seed.sql       8 个驿站、10 条线路、9 个操作员、16 名快递员
  test.sql       32 条生命周期断言（事务内执行，结束回滚）
docs/API.md      接口文档
web/             前端工程（见 web/README.md）
```

## 快速开始

### 1. 数据库

```bash
mysql -h127.0.0.1 -P3308 -uroot -p < sql/schema.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/seed.sql
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 可选：应输出「32 条用例全部通过」
```

### 2. 构建前端（必须）

```bash
cd web
npm install
npm run build        # 产物写入 src/main/resources/static
```

> **`src/main/resources/static/` 是构建产物，没有入库。**
> 从仓库拉下来后如果不执行这一步，后端只有接口，访问 `/` 是空的。
> 需要现场改前端时用 `npm run dev`（http://127.0.0.1:5173，`/api` 走代理）。
> 把构建产物一并提交进仓库也可以，去掉 `.gitignore` 里对应那一行即可。

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
mysql -h127.0.0.1 -P3308 -uroot -p < sql/test.sql   # 32 条数据库层断言

cd web
node scripts/flow-check.mjs     # 登录拦截 / 批准 / 拒绝 / 刷新保持会话
node scripts/shots.mjs          # 截图到 shots/（用本机 Edge，不下载浏览器）
```

## 文档

- 接口文档：[docs/API.md](docs/API.md)——通用约定、错误码、状态机、25 个接口的请求响应、权限矩阵
- 前端说明：[web/README.md](web/README.md)——页面结构、开发与构建

## 已知限制

1. Session 存在内存，重启即失效；多实例部署需集中式 Session。
2. 口令为 SHA-256 + 盐，无工作因子；生产应换 BCrypt / Argon2。
3. 二维码不防伪造，拿到单号即可查询订单详情（与"凭单号查询"的业务设定一致）。
4. 退回单被管理员拒绝后，货物的线下处置不在系统范围内。
5. 列表接口未分页，数据量上去后需要补。
