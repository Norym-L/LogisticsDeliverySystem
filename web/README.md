# 路径管理台（前端）

Vue 3 + TypeScript + Vite，设计语言取自 `milk-white-web-design`：浅灰/乳白纸感材质、
左上高光 + 右下柔影的圆角卡片、胶囊按钮、内凹输入。

## 页面

| 路由 | 内容 |
|---|---|
| `/login` | 路径管理员登录（节点管理员会被拒绝） |
| `/` | 待审批列表、推荐路线预览、批准 / 拒绝 |
| `/postmen` | 快递员档案、手里的单（待送 / 已完成） |

## 开发

后端先跑起来（默认 18080）：

```bash
cd ..
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
```

前端：

```bash
npm install
npm run dev          # http://127.0.0.1:5173，/api 走 Vite 代理
```

## 构建到 Spring Boot

```bash
npm run build        # 产物直接写入 ../src/main/resources/static
```

之后 `mvn package` 会把页面打进 jar，访问 `http://127.0.0.1:18080/` 即可。

> `src/main/resources/static/` 是构建产物，已在 `.gitignore` 中忽略。
> 从仓库拉下来后需要先 `npm run build`，否则后端只有接口没有页面。

## 验证脚本

```bash
node scripts/shots.mjs      # 截图到 shots/，需要本机 Edge
node scripts/flow-check.mjs # 登录拦截 / 批准 / 拒绝 / 刷新保持会话
```

两个脚本都用 Playwright 驱动本机已安装的 Edge（`channel: 'msedge'`），不下载浏览器。

## 设计说明

- 材质、阴影、圆角、滚动手感全部来自 `src/styles/milk-tokens.css`，未自行改数值。
- 主题在侧栏可切换纸白 / 乳白 / 石墨，选择记在 `localStorage`。
- 演示账号：`admin / 123456`。
