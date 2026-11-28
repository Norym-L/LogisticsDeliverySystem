# 通驿 · 前端

Vue 3 + TypeScript + Vite，设计语言取自 `milk-white-web-design`：浅灰/乳白纸感材质、
左上高光 + 右下柔影的圆角卡片、胶囊按钮、内凹输入；各门户统一使用左右分栏布局。

## 页面

| 路由 | 角色 | 内容 |
|---|---|---|
| `/login` | 路径管理员 | 登录（节点管理员会被拒绝） |
| `/` | 路径管理员 | 待审批列表、推荐路线预览、预计时效、批准 / 拒绝 |
| `/lookup` | 路径管理员 | 按单号查已审批订单，含轨迹地图 |
| `/postmen` | 路径管理员 | 快递员档案、手里的单 |
| `/user` | 用户 | 寄件下单、运费与时效试算、按手机号查单、按运单号查件 |
| `/user/orders/:orderNo` | 用户 | 订单详情、轨迹地图、路线节点、二维码、预计时效、取消未发运订单、签收 / 拒收、被拒后重提 |
| `/stations` | 用户 | 只读网点列表（站名 + 地址） |
| `/station/login` | 节点管理员 | 登录 |
| `/station` | 节点管理员 | 本站待办：确认收件并核价、登记车次、发运、到货、派单 |
| `/courier/login` | 快递员 | 登录 |
| `/courier` | 快递员 | 始发收件核价、单号定位、取件、妥投（本人/电话）、派送失败、最终运费 |

## 开发

后端先跑起来（默认 18080）：

```bash
cd ..
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

前端：

```bash
npm install
cp .env.example .env.local   # 编辑 .env.local，填写高德 Web 端 JS API Key
npm run dev          # http://127.0.0.1:5173，/api 走 Vite 代理
npm run typecheck    # vue-tsc
npm run build        # 产物直接写入 ../src/main/resources/static
```

`.env.local` 里的 `VITE_AMAP_JS_API_KEY` 只在本机使用，已被 `.gitignore` 忽略，不要提交。
构建时也兼容进程环境变量 `AMAP_JS_API_KEY`（优先级低于 `.env.local`）。
没有 Key 时轨迹地图区域会显示明确提示，不会回退到 OpenStreetMap，也不会使用写死的示例 Key。
Key 需在高德控制台配置当前访问域名白名单。

之后 `mvn package` 会把页面打进 jar，访问 `http://127.0.0.1:18080/` 即可。

> `src/main/resources/static/` 是构建产物，已在 `.gitignore` 中忽略。
> 从仓库拉下来后需要先 `npm run build`，否则后端只有接口没有页面。

## 验证脚本

```bash
node scripts/shots.mjs      # 截图到 shots/，需要本机 Edge
node scripts/flow-check.mjs # 登录拦截 / 批准 / 拒绝 / 刷新保持会话
```

两个脚本都用 Playwright 驱动本机已安装的 Edge（`channel: 'msedge'`），不下载浏览器。
没有 Edge 时可把 `channel` 改成 `chromium` 并指定本机 Chrome 的 `executablePath`。

## 设计说明

- 材质、阴影、圆角、滚动手感全部来自 `src/styles/milk-tokens.css`，未自行改数值。
- 主题在侧栏可切换纸白 / 乳白 / 石墨，选择记在 `localStorage`。
- 运单二维码由 `qrcode` 包在前端绘制；后端只返回单号字符串。
- 轨迹地图用高德 JS API 2.0 官方底图，坐标系为 GCJ-02；底图 logo 与版权信息由高德默认控件保留。
  站点经纬度来自 `/api/station/list` 和订单详情的 `trajectory[]` / `currentPosition`（GCJ-02，
  已在数据库完成 WGS84→GCJ-02 转换），前端不维护第二份坐标表，也不再假设 WGS84。
  当前位置（在站/在途/派送/签收/拒收）由后端按状态机算好，在途取相邻两站中点。
- 地图只画转运中心之间的折线和当前标记，不接车辆 GPS，也不做门到门导航。
- 演示账号：路径管理员 `admin / 123456`，节点管理员 `st001` ~ `st008 / 123456`，
  快递员 `pm001` ~ `pm016 / 123456`。
