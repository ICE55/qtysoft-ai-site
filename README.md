# 乾腾元官网（生产级静态站点）

依据 Ardot 设计稿（fileId `721718316795995`，桌面 5 页 + 移动 5 页）1:1 还原的企业官网。
纯静态输出，零第三方运行时依赖，任何静态托管（Nginx / OSS / COS / Vercel / Netlify）直接可上线。

当前平台预览：https://72cc39bccdad47d592b779b546023345.app.workbuddy.link
正式域名：`https://www.qtysoft-ai.com`（Vercel 托管，见下方「部署到 Vercel」）

> 平台预览链接由沙箱 ID 生成，不支持自定义子域名。要拿自定义地址必须绑定自己的域名。

## 部署到 Vercel（正式域名 qtysoft-ai.com）

`vercel.json` 已配好：`buildCommand: node build.mjs`、`outputDirectory: dist`、
静态资源缓存策略与安全响应头、`/index` `/home` 301 到根。

**方式一：命令行（最快）**

```bash
npx vercel --prod          # 首次按提示登录并选择项目
npx vercel --prod          # 之后每次改完重新构建 + 部署
```

**方式二：GitHub 自动部署**

1. 把仓库推到 GitHub；
2. Vercel → Add New → Project → Import 该仓库；
3. 构建配置会自动读 `vercel.json`，直接 Deploy；
4. 之后每次 push 自动上线。

**绑定域名（Vercel 控制台操作）**

1. 项目 → Settings → Domains → Add，依次添加 `qtysoft-ai.com` 和 `www.qtysoft-ai.com`；
2. Vercel 会给出 DNS 记录，去域名注册商处添加（推荐方式见下表）；
3. 把 `www.qtysoft-ai.com` 设为 **Primary**，勾上「Redirect qtysoft-ai.com → www」；
4. Vercel 自动签发 HTTPS 证书，等 DNS 生效（一般 5 分钟 ~ 24 小时）即可访问。

| 主机记录 | 记录类型 | 记录值 | 说明 |
| --- | --- | --- | --- |
| `www` | CNAME | `cname.vercel-dns.com` | 主访问域名 |
| `@` | A | `76.76.21.21` | 根域名，由 Vercel 重定向到 www |

> 若域名注册商不支持根域名 A 记录（如部分国内服务商），改用 **DNS 托管到 Vercel**
> （Vercel 会给出两个 NS 记录），一次性解决根域名与证书签发。

**换域名时只需改一处**：`build.mjs` 里的 `SITE_URL`（或构建时传环境变量 `SITE_URL=https://xxx.com node build.mjs`）。
canonical、sitemap、OG 地址、业务邮箱全部由它派生，无需逐个文件替换。

## 快速开始

```bash
npm run build     # 构建到 dist/
npm run serve     # 本地预览 http://localhost:4173
npm run watch     # 监听 src/ 改动自动重建
npm start         # 构建 + 预览
```

> 无需 `npm install`，构建与预览脚本只用 Node 内置模块（>= 18）。

## 目录结构

```
qty-ai-site/
├─ build.mjs              零依赖静态生成器：frontmatter + 片段拼装 + 导航高亮
├─ serve.mjs              零依赖本地预览服务（含 404 兜底）
├─ src/
│  ├─ pages/              6 个页面，顶部 frontmatter 定义 title / description / nav
│  │  ├─ index.html       首页
│  │  ├─ product.html     产品能力
│  │  ├─ solutions.html   行业解决方案
│  │  ├─ cases.html       客户案例
│  │  ├─ about.html       关于我们（含预约表单）
│  │  └─ 404.html
│  ├─ partials/           head / header / cta-section / footer（可嵌套 include）
│  ├─ assets/css/         tokens.css（设计令牌）· components.css · sections.css
│  ├─ assets/js/main.js   移动导航 / 滚动进场 / 数字滚动 / 表单校验
│  ├─ robots.txt          （模板，Sitemap 地址由 SITE_URL 渲染）
│  ├─ favicon.svg · og-cover.svg
├─ vercel.json            Vercel 部署配置（构建命令 / 输出目录 / 缓存 / 重定向）
└─ dist/                  构建产物（直接部署这个目录；sitemap.xml 由构建自动生成）
```

## 技术实现要点

| 项目 | 做法 |
| --- | --- |
| 设计令牌 | 全部颜色 / 圆角 / 字号 / 阴影集中在 `tokens.css` 的 `:root`，改一处全站生效 |
| 字体 | 系统字体栈（PingFang SC / 微软雅黑 / Noto Sans SC），不加载中文字体包，首屏零字体阻塞 |
| 布局 | CSS Grid + 语义化标签；断点 1080 / 900 / 768 / 480；容器 1200 + 24px 边距（移动端 20px） |
| 视觉位 | 设计稿里的占位图全部改为纯 CSS/HTML 模拟界面（控制台、流程编排、连接器、看板），无图片请求 |
| 动效 | IntersectionObserver 进场动画 + 数字滚动，全部遵循 `prefers-reduced-motion` |
| 无障碍 | 跳转链接、`aria-expanded` / `aria-current`、`:focus-visible` 焦点环、`role="status"` 表单反馈 |
| SEO | 每页独立 title/description/canonical、OG & Twitter 卡片、Organization JSON-LD、sitemap、robots |
| 表单 | 前端校验（公司 / 联系人 / 手机或邮箱 / 场景）+ 提交态与失败兜底 |

## 上线前需要替换的内容

| 位置 | 现状 | 需要替换成 |
| --- | --- | --- |
| `partials/footer.html` | 浙ICP备 2024XXXXXX 号 / 浙公网安备 3301XXXXXXXXXXXX 号 | 真实备案号（国内服务器强制） |
| `assets/js/main.js` | 预约表单提交 = `mailto:` 起草到 `coolxuhanbing@gmail.com`（收件人写在 `about.html` 表单 `data-email`） | 如需"零点击真·自动发送"，改接 EmailJS / Formspree（详见下方「表单自动发送」） |
| 全站数据 | 320+ / 80+ / 42% / 15 天 / 95% 等 | 真实业务数据 |
| 客户与人名 | 远海制造 / 浙商金控 / 万象零售 / 云栖医疗 / 数城政务、王志远 | 已获授权的真实信息 |
| `build.mjs` 的 `SITE_EMAIL` | `hi@qtysoft-ai.com` | 确认该邮箱已创建（或设环境变量 `SITE_EMAIL`） |

> 换域名：`SITE_URL`（默认 `https://www.qtysoft-ai.com`）。业务邮箱按主域名自动派生，
> 也可用 `SITE_EMAIL` 单独覆盖。

## 表单自动发送

预约表单（`/about.html#contact`，`id="leadForm"`）目前采用 **`mailto:` 起草** 方式：
访客点「提交预约」→ 前端校验 → 自动调起本机邮件客户端，生成一封发往 `coolxuhanbing@gmail.com`
的草稿（含公司 / 联系人 / 联系方式 / 意向场景 / 提交时间），访客点「发送」即送达。
收件人在 `about.html` 的 `<form data-email="...">` 上改一处即可。

这是**纯静态托管（无后端）下的可行方案**，但有前提：访客本机需配置邮件客户端，
且最终需访客手动点发送才真正寄出。

### 升级为零点击「真·自动发送」（任选其一，均无需自建后端）

- **EmailJS（推荐，纯前端）**：注册 EmailJS → 建 Service + Template → 把
  `YOUR_SERVICE_ID` / `YOUR_TEMPLATE_ID` / `YOUR_PUBLIC_KEY` 填入 `main.js` 的提交分支，
  用 `emailjs.send(serviceId, templateId, { company, name, contact, scene, time }, publicKey)`
  替换当前的 `mailto` 跳转即可。
- **Formspree / Getform**：注册后拿到表单端点（如 `https://formspree.io/f/xxxx`），
  把 `main.js` 里的 `mailto` 换成 `fetch(endpoint, { method:'POST', body:new FormData(form) })`，
  提交内容会由服务商直接转发到 `coolxuhanbing@gmail.com`。

两种方案都只需改动 `main.js` 一个提交分支，其余校验 / UI 不变。

## 与移动端设计稿的对应

移动版（375 宽设计稿 M1–M5）的规则已落到 CSS：多列网格改单列、数据格改 2 列、
字号按 clamp 收敛（H1 62→33、H2 40→25、正文 16→14）、导航改抽屉、
底部新增吸底咨询条（Mobile Sticky CTA Bar）。
