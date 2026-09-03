# 乾腾元官网 · 技术文档

> 版本：v1.0 ｜ 日期：2026-09-03 ｜ 关联设计稿：Ardot `fileId 721718316795995`
> 本文描述官网的**技术架构、构建流程、部署方式**，作为开发与运维的参考基准。

---

## 1. 技术选型与理由

| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 站点形态 | **纯静态站点**（构建时生成 HTML） | 官网以展示为主，无需服务端渲染；静态托管成本低、速度快、易部署到任意平台 |
| 运行时依赖 | **零第三方运行时依赖** | 全部使用浏览器原生能力 + 系统字体，无框架包体，首屏无外部请求 |
| 构建工具 | 自研 `build.mjs`（仅 Node 内置模块） | 功能聚焦（片段拼装 + 变量替换 + sitemap 生成），无需引入打包器，维护简单 |
| 样式 | 原生 CSS + CSS 变量（设计令牌） | 令牌化便于全站统一调整，无编译步骤 |
| 部署 | 多通道：WorkBuddy / GitHub Pages / Vercel | 同一份 `dist/` 可投任意静态托管 |

**不引入** React/Vue/Next.js/Tailwind 等 — 对单页展示型官网属于过度工程，且增加构建复杂度与包体。

---

## 2. 系统架构

```
源码 (src/)  ──build.mjs──▶  产物 (dist/)  ──▶  静态托管
  pages/                       index.html           WorkBuddy 预览
  partials/                    product.html         GitHub Pages
  assets/                      solutions.html        Vercel (qtysoft-ai.com)
  robots.txt / sitemap 模板     cases.html
                                about.html / 404.html
                                assets/ (css/js/svg)
                                robots.txt / sitemap.xml
```

- **构建时**：`build.mjs` 解析每个页面的 frontmatter、拼装片段、注入模板变量、生成相对路径与 `sitemap.xml`，输出纯静态 `dist/`。
- **运行时**：浏览器直接加载 `dist/`，无任何后端 / serverless 调用（表单提交为 `mailto:` 起草，见 §10）。

---

## 3. 目录结构

```
qty-ai-site/
├─ package.json            # 脚本与元信息
├─ build.mjs              # 零依赖静态站点生成器
├─ serve.mjs              # 本地预览（Node http，仅内置模块）
├─ vercel.json            # Vercel 部署配置
├─ .github/workflows/
│   └─ deploy.yml         # GitHub Pages 自动部署
├─ scripts/
│   └─ sync.sh            # 重建 + 提交 + 推送 一键同步
├─ docs/
│   ├─ PRD.md
│   └─ TECH.md
├─ src/
│   ├─ pages/             # 6 个页面（含 frontmatter）
│   ├─ partials/          # head / header / footer / cta-section
│   ├─ assets/
│   │   ├─ css/           # tokens / components / sections
│   │   └─ js/main.js     # 交互逻辑
│   ├─ favicon.svg
│   ├─ og-cover.svg
│   ├─ robots.txt
│   └─ (sitemap.xml 构建时生成，不入库)
└─ dist/                  # 构建产物（git 忽略）
```

> `dist/` 与 `.DS_Store` 已写入 `.gitignore`。

---

## 4. 构建流程（build.mjs）

`node build.mjs` 依次执行：

1. **读取页面**：扫描 `src/pages/*.html`。
2. **解析 frontmatter**：页面顶部 `---\ntitle: ...\ndescription: ...\nnav: ...\n---` 块，提取页面级元数据。
3. **拼装片段**：处理 `<!-- @include partials/xxx.html -->`（支持嵌套），将 head / header / footer / cta-section 注入。
4. **注入模板变量**：`{{title}}` `{{description}}` `{{canonical}}` `{{year}}` `{{email}}` 等按页面与全局变量替换。
   - 全局变量单一来源：`SITE_URL`（默认 `https://www.qtysoft-ai.com`），派生出 `SITE_HOST` 与 `SITE_EMAIL`（`hi@<host>`），可用环境变量 `SITE_URL` / `SITE_EMAIL` 覆盖。
5. **相对路径改写**：将根绝对路径（`/assets/...`、`/product.html`）改写为 `./` 同级相对路径，保证部署到子路径时不 404；canonical / OG 仍保留带域名的绝对地址。
6. **生成 sitemap.xml**：首页优先、自动排除 404，loc 使用 `SITE_URL`。
7. **拷贝静态资源**：`assets/`、`robots.txt`、`favicon.svg`、`og-cover.svg` 复制进 `dist/`。

支持 `node build.mjs --watch` 监听 `src/` 变化自动重建。

---

## 5. 设计令牌系统（tokens.css）

所有颜色 / 间距 / 字号 / 圆角 / 阴影集中在 `:root`，改一处即全站生效：

- **底色**：`--bg #060912`、`--bg-alt #070C18`、`--surface #0C1425` …
- **品牌色**：`--brand #3B7BFF`、`--violet #8B5CF6`、`--cyan #22D3EE` …
- **文字**：`--text #F2F5FF`、`--text-2 #97A3C0` …
- **圆角**：`--r-xs 6px` … `--r-pill 999px`
- **辉光/阴影**：`--glow-brand`、`--shadow-card` …
- **布局**：`--container 1200px`、`--gutter 24px`、`--header-h 76px`、`--section-y 104px`
- **字体**：系统中文栈（`PingFang SC` / `Noto Sans SC` 优先）
- **动效**：`--ease cubic-bezier(.4,0,.2,1)`、`--dur .28s`

排版尺度用 `clamp()` 实现桌面→移动线性收敛：`.t-display` 62→33px、`.t-h2` 40→25px 等。

---

## 6. 样式分层

| 文件 | 职责 |
| --- | --- |
| `tokens.css` | 设计令牌 + 重置 + 排版尺度 + 布局基元（`.container`/`.section`）+ 无障碍/动效 |
| `components.css` | 可复用组件：按钮、卡片、徽标、表单、导航、页脚、标签、网格 |
| `sections.css` | 各页面区块样式：Hero、能力矩阵、流程、案例卡、CTA 区等 |

---

## 7. 片段 / 组件体系

| 片段 | 作用 | 说明 |
| --- | --- | --- |
| `head.html` | `<head>` 元信息 + SEO/OG/JSON-LD | 接收 `{{title}}` `{{description}}` `{{canonical}}` `{{year}}` `{{email}}` |
| `header.html` | 顶部导航 | 5 栏目 + 预约演示 CTA；构建时按 `nav` 注入当前页高亮 |
| `footer.html` | 页脚 | 品牌区 + 三列链接 + 底栏版权/ICP 占位 |
| `cta-section.html` | 预约演示行动区 | 各核心页面底部复用 |

---

## 8. 前端交互（main.js）

原生 JS，无框架，主要模块：
- **移动导航抽屉**：汉堡开合、body 滚动锁定。
- **滚动进场**：`IntersectionObserver` 给 `.reveal` 元素加 `is-visible`。
- **数字滚动**：带 `data-count` 的元素进入视口时从 0 滚动到目标值。
- **表单校验与提交**：关于我们页预约表单，前端校验后 `mailto:` 起草到 `data-email` 收件人（见 §10）。
- **导航吸顶 / 平滑锚点**：依赖 CSS `scroll-behavior` 与 `scroll-padding-top`。

---

## 9. 占位图方案

设计稿中的「控制台截图 / 产品界面 / 案例配图」等，全部用**纯 CSS / HTML 模拟界面**实现（如运营控制台、流程编排画布、连接器宫格、KPI 看板、案例指标卡），零图片请求、无需等待真实素材。正式上线可替换为真实截图（放入 `src/assets/` 并在页面引用）。

---

## 10. 表单提交方案与升级路径

### 当前实现：`mailto:` 起草
- 校验通过后，`main.js` 拼装 `mailto:<data-email>?subject=...&body=...`（含公司/联系人/联系方式/场景/提交时间），`window.location.href = mailto` 调起访客邮件客户端。
- 收件人写在 `<form data-email="coolxuhanbing@gmail.com">`，改一处即可换邮箱。
- **限制**：依赖访客本机已配置邮件客户端，且需访客手动点「发送」才真正寄出；浏览器无法直连 SMTP。

### 升级为真·自动发送（零后端，可选）
| 方案 | 改动 |
| --- | --- |
| **EmailJS**（推荐） | 注册后填 Service ID / Template ID / Public Key，将 `mailto` 分支替换为 `emailjs.send(...)` |
| **Formspree / Getform** | 注册拿表单端点，替换为 `fetch(endpoint, {method:'POST', body})`，服务商直接转投邮箱 |

两者都只需改 `main.js` 的提交分支，校验与 UI 不动。

---

## 11. 响应式方案

- 断点约定：桌面 ≥1024、平板 768–1023、移动 ≤768（以 CSS 媒体查询为准）。
- 栅格：桌面多列（能力矩阵 3 列、案例/特性 2 列），移动端降为单列。
- 字号：`clamp()` 线性收敛（见 §5）。
- 导航：≤768 折叠为抽屉。
- 底部吸底咨询条：移动端固定展示「预约演示」入口。

---

## 12. SEO 与生产化细节

- 每页独立 `<title>` / `<meta name="description">` / canonical。
- OG / Twitter Card（`og-cover.svg`）、Organization JSON-LD。
- `sitemap.xml`（构建时生成）、`robots.txt`、`favicon.svg`。
- 系统字体栈，不加载中文字体包。
- `prefers-reduced-motion` 降级、可见焦点环、skip-link、语义化标签。

---

## 13. 部署方案

### 13.1 WorkBuddy 静态托管（当前预览）
`dist/` 直接发布，访问 `https://72cc39bccdad47d592b779b546023345.app.workbuddy.link`。
> 注：该链接由沙箱 ID 生成，不支持自定义子域名。

### 13.2 GitHub Pages（已上线）
- 仓库 `ICE55/qtysoft-ai-site`（Public），分支 `main`。
- `.github/workflows/deploy.yml`：push 到 `main` 自动 `node build.mjs` → 上传 `dist/` → 部署到 Pages。
- 访问：`https://ice55.github.io/qtysoft-ai-site/`。

### 13.3 Vercel（正式域名 qtysoft-ai.com）
- `vercel.json`：`buildCommand: node build.mjs`、`outputDirectory: dist`、`cleanUrls: false`（保留 `.html` 后缀，与 sitemap/canonical 一致）、静态资源缓存与安全响应头、`/index` `/home` 301 到根。
- 绑定域名：Vercel 控制台 Add Domain，DNS 加 `www` CNAME 到 `cname.vercel-dns.com`（或根域名 A 记录 `76.76.21.21`）。

---

## 14. 开发工作流

```bash
npm run build     # 构建到 dist/
npm run watch     # 监听重建
npm run serve     # 本地预览（默认 4183 端口）
npm start         # 构建 + 预览
npm run sync      # 重建 + git 提交 + 推送 origin main（一键同步 GitHub）
```

`scripts/sync.sh` 逻辑：运行 `build.mjs` → `git add -A` → 有变更则 `commit` → `git push origin main`。**后续任何改动均通过 `npm run sync` 留痕到 GitHub，自动触发 Pages 部署。**

---

## 15. 上线前待替换命令参考

```bash
# 换正式域名重新构建（Vercel / 自定义域名时）
SITE_URL=https://www.qtysoft-ai.com node build.mjs

# 单独覆盖业务邮箱
SITE_EMAIL=bd@qtysoft-ai.com node build.mjs
```

需人工替换的内容见 PRD §10（备案号、真实邮箱/地址、示例数据、虚构客户名、表单接收邮箱/第三方端点）。

---

## 16. 已知限制与后续演进

- **表单非真·自动发送**：当前 `mailto` 方案依赖访客邮件客户端，详见 §10。
- **占位图非真实素材**：见 §9，上线前替换为真实截图。
- **无埋点 / 无 A/B**：如需数据驱动优化，后续可接入轻量分析。
- **无 CMS**：内容修改需改源码后 `npm run sync`。
- **单语言**：暂未做国际化。
