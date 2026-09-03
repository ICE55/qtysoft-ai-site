# 乾腾元官网 · CMS 技术文档（CMS-TECH）

> 版本：v0.3（v0.2 升级 Docker 化部署）｜ 日期：2026-09-03 ｜ 关联：《CMS 产品需求文档》CMS-PRD.md、《技术文档》TECH.md
> 状态：**规划中 — 确认后进入实现**

---

## 1. 总体架构

```
┌──────────────────────────────┐
│  Vue 3 控制台 (Vite 构建)     │
│  Pinia + Vue Router + Axios   │
│  Element Plus (UI)            │
└──────────────┬───────────────┘
               │ HTTPS + JSON (JWT Bearer)
               ▼
┌──────────────────────────────────────────────────────────┐
│  cms-backend 容器 (Spring Boot 3, JRE 17 slim)            │
│  ├─ auth      : Spring Security 6 + JWT, BCrypt, RBAC     │
│  ├─ content   : 文档 CRUD（site/home/product/...）         │
│  ├─ revision  : 版本快照 + 回滚                           │
│  └─ publish   : 标记已发布 + 触发静态站重建                │
└──────────────┬───────────────────────────────────────────┘
               │ JPA / JDBC
               ▼
        ┌──────────────┐        部署令牌拉取已发布内容
        │ cms-db 容器   │ ───────────────────────────┐
        │ PostgreSQL    │                            ▼
        │ (命名卷持久化) │             ┌─────────────────────────┐
        └──────────────┘             │ 静态站构建 (build.mjs)    │
                                      │ --cms 模式拉取 published │
                                      └───────────┬─────────────┘
                                                  ▼
                                    GitHub Pages / Vercel（公开官网）
```

核心原则：**CMS 是内容的生产与存储系统（Vue3+Spring Boot+PostgreSQL，整栈 Docker 交付）；公开官网仍是纯静态站，仅在构建时从 CMS 拉取已发布内容。** 两者通过「发布服务 → 触发重建」衔接；公开官网与 CMS 不在同一 compose 编排内。

---

## 2. 后端（Spring Boot 3）

### 2.1 技术选型
| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 框架 | Spring Boot 3 + Java 17 | 成熟、生态全、镜像小（slim JRE） |
| 安全 | Spring Security 6 + JWT（jjwt）+ BCrypt | 自托管账号体系，无外部依赖 |
| 持久层 | Spring Data JPA（Hibernate）+ PostgreSQL | JSONB 直接存内容、版本表快照方便 |
| 构建 | Gradle（或 Maven） | 标准 |
| 迁移 | 可选 Flyway | 表结构版本化（v1 可省略，直接用 JPA ddl-auto=update） |
| 运行 | **Docker 镜像（多阶段构建）** | 与前端、数据库统一编排 |

### 2.2 数据模型（PostgreSQL）
```sql
-- 账号（CMS 自身，与 GitHub 无关）
CREATE TABLE cms_users (
  id            BIGSERIAL PRIMARY KEY,
  username      VARCHAR(64) UNIQUE NOT NULL,
  password_hash VARCHAR(100) NOT NULL,     -- BCrypt
  role          VARCHAR(20)  NOT NULL DEFAULT 'EDITOR', -- SUPER_ADMIN/EDITOR/VIEWER
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 内容文档（草稿态）
CREATE TABLE cms_documents (
  id         BIGSERIAL PRIMARY KEY,
  doc_key   VARCHAR(40) UNIQUE NOT NULL,   -- site/home/product/solutions/cases/about
  data_json  JSONB NOT NULL,               -- 当前草稿内容
  status     VARCHAR(10) NOT NULL DEFAULT 'draft', -- draft/published
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT REFERENCES cms_users(id)
);

-- 版本快照（每次发布写一条）
CREATE TABLE cms_revisions (
  id         BIGSERIAL PRIMARY KEY,
  doc_id     BIGINT REFERENCES cms_documents(id),
  doc_key    VARCHAR(40) NOT NULL,
  data_json  JSONB NOT NULL,
  note       VARCHAR(200),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by BIGINT REFERENCES cms_users(id)
);

-- 预留：素材（v1 不启用）
CREATE TABLE cms_media (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(200), path VARCHAR(300), created_at TIMESTAMPTZ DEFAULT now()
);
```

### 2.3 REST API
| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 登录，返回 JWT | 公开 |
| POST | `/api/auth/logout` | 退出（客户端弃令牌） | 登录 |
| GET | `/api/auth/me` | 当前账号信息 | 登录 |
| GET | `/api/content/schema` | 内容 schema（驱动表单） | 登录 |
| GET | `/api/content/{key}` | 取草稿 | 登录 |
| PUT | `/api/content/{key}` | 存草稿 | EDITOR+ |
| POST | `/api/content/{key}/publish` | 标记已发布 + 写快照 + 触发重建 | EDITOR+ |
| GET | `/api/content/{key}/history` | 版本列表 | 登录 |
| POST | `/api/content/{key}/restore/{revId}` | 回滚到某版本 | EDITOR+ |
| GET | `/api/content/published` | **取全部已发布内容**（供构建拉取） | 部署令牌 |
| POST | `/api/system/deploy` | 手动触发静态站重建 | SUPER_ADMIN |
| GET/POST | `/api/system/users` | 账号管理 | SUPER_ADMIN |

### 2.4 认证（CMS 自身）
- 登录：`/api/auth/login` 校验用户名 + BCrypt 密码 → 签发 JWT（HS256，密钥取自环境变量 `CMS_JWT_SECRET`，短期如 2h，可选 refresh）。
- 客户端：Axios 拦截器统一在 `Authorization: Bearer <jwt>` 携带；401 跳登录。
- 授权：Spring Security 方法级 `@PreAuthorize("hasRole('EDITOR')")` 等，按角色拦截。
- 种子管理员：首次启动若 `cms_users` 为空，用 `CMS_ADMIN_USER` / `CMS_ADMIN_PASS` 建超管，登录后强制改密。
- CORS：仅放行控制台域名（`CMS_CORS_ORIGINS`）；生产关闭 debug。

### 2.5 发布服务
- `publish` 接口：① 把 `data_json` 标记为 `published`；② 向 `cms_revisions` 插一条快照；③ 异步调用部署钩子。
- 部署钩子二选一（见 §5）：GitHub Actions `workflow_dispatch` / Vercel Deploy Hook。钩子地址与令牌存配置，不在代码写死。

---

## 3. 前端（Vue 3）

### 3.1 技术选型
- **Vue 3** + **Vite** + **Pinia**（状态）+ **Vue Router**（路由）+ **Axios**（API）。
- UI：**Element Plus**（表单 / 表格 / 对话框 / 消息），深色主题适配品牌色。
- 构建产物为静态文件，由 `cms-console` 镜像内的 nginx 托管。

### 3.2 目录结构（建议）
```
cms-admin/
├─ index.html
├─ vite.config.js          # 构建为静态站
├─ Dockerfile              # 多阶段：node 构建 → nginx 托管
├─ nginx.conf              # SPA 路由 fallback + 反向代理 /api
├─ src/
│  ├─ main.js
│  ├─ App.vue
│  ├─ router/              # 登录 / 仪表盘 / 各编辑器 / 历史 / 系统
│  ├─ stores/              # auth.js（JWT）、content.js（草稿+已发布）
│  ├─ api/                 # axios 实例 + interceptor + 各资源接口
│  ├─ views/               # Login / Dashboard / SiteSettings / PageEditor / History / System
│  ├─ components/          # SchemaField、PreviewPane、ListEditor
│  └─ styles/              # 深色主题变量（沿用官网令牌）
```

### 3.3 Schema 驱动表单
- 后端 `/api/content/schema` 返回每文档字段定义（类型 / 必填 / 长度 / 列表元信息）。
- `SchemaField` 组件按类型渲染：text / textarea / number / list（可增删 + 上下移）/ select。
- 这样新增字段只需后端改 schema + 模板加占位，前端无需改代码。

### 3.4 预览
- `PreviewPane` 以 iframe 或组件方式套用线上 CSS（从公开站拉取 `sections.css` / `components.css`），把当前编辑内容渲染出来，做到「所见即所得」。

---

## 4. 内容存储与版本

- 每个文档 = 一段 JSONB（`cms_documents.data_json`），结构即 CMS-PRD §4 内容模型。
- 每次 `publish` 写一条 `cms_revisions` 快照；回滚 = 把某快照 PUT 为草稿再发布。
- 草稿与已发布分离：`GET /{key}` 取草稿，`/published` 只暴露已发布，保证线上不被半成品影响。

---

## 5. 公开发布链路（关键）

公开官网保持纯静态，内容在**构建时**从 CMS 拉取：

1. 编辑在控制台点「发布」→ Spring Boot 标记 `published` + 写快照 → 调用部署钩子。
2. 钩子触发 GitHub Actions `workflow_dispatch`（或 Vercel Deploy Hook）。
3. Actions 中运行 `node build.mjs --cms`，设置环境变量：
   - `CMS_API_URL=https://<cms-host>/api/content/published`
   - `CMS_DEPLOY_TOKEN=<只读部署令牌>`（仅能调 `/published`）
4. `build.mjs --cms`：拉取全部已发布文档 JSON → 解析点号路径变量 → 渲染模板 → 输出 `dist/`。
5. Pages / Vercel 部署 `dist/`，线上更新。

> **兜底**：`build.mjs` 支持无 `--cms` 时回退读取本地 `content/*.json`（开发者离线改源码 + `npm run sync` 仍可用）。

---

## 6. Docker 容器化部署（v0.3 核心）

整栈 CMS（后端 + PostgreSQL + 控制台）以 Docker 交付，一条命令起整套。

### 6.1 镜像规划

| 镜像 | 基础 | 内容 | 端口 |
| --- | --- | --- | --- |
| `cms-backend` | `eclipse-temurin:17-jre` | Spring Boot fat-jar | 8080 |
| `cms-db` | `postgres:16-alpine` | PostgreSQL 数据（命名卷） | 5432（仅内网） |
| `cms-console` | `nginx:1.27-alpine` | Vite 构建产物 + nginx 反代 | 80/443 |

### 6.2 后端 Dockerfile（多阶段）
```dockerfile
# ---- build ----
FROM eclipse-temurin:17 AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar -x test || ./mvnw -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```
> 用 `SPRING_PROFILES_ACTIVE=docker` 加载容器专用配置；所有连接串 / 密钥走环境变量注入。

### 6.3 控制台 Dockerfile（多阶段 + nginx）
```dockerfile
# ---- build ----
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build            # 输出 dist/

# ---- serve ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```
`nginx.conf` 关键：SPA `try_files` fallback 到 `index.html`；`/api` 反向代理到 `cms-backend:8080`（同 compose 网络）。

### 6.4 docker-compose.yml（编排）
```yaml
services:
  cms-db:
    image: postgres:16-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - cms_db_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 10s
      timeout: 5s
      retries: 5

  cms-backend:
    image: ${REGISTRY}/cms-backend:${TAG:-latest}
    restart: unless-stopped
    depends_on:
      cms-db:
        condition: service_healthy
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://cms-db:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      CMS_JWT_SECRET: ${CMS_JWT_SECRET}
      CMS_ADMIN_USER: ${CMS_ADMIN_USER}
      CMS_ADMIN_PASS: ${CMS_ADMIN_PASS}
      CMS_CORS_ORIGINS: ${CMS_CORS_ORIGINS}
      DEPLOY_HOOK_URL: ${DEPLOY_HOOK_URL}
      CMS_DEPLOY_TOKEN: ${CMS_DEPLOY_TOKEN}
    ports:
      - "8080:8080"

  cms-console:
    image: ${REGISTRY}/cms-console:${TAG:-latest}
    restart: unless-stopped
    depends_on:
      - cms-backend
    ports:
      - "80:80"
      # 443:443  # 接入 TLS 时启用，配合反向代理/证书

volumes:
  cms_db_data:
```
> 全部敏感值来自 `.env`（不进仓库、不进镜像）。`REGISTRY` / `TAG` 支持私有镜像仓库与版本回滚。

### 6.5 持久化与迁移
- PostgreSQL 数据落在命名卷 `cms_db_data`，容器重建 / 升级镜像**不丢数据**。
- 迁移到新机器：备份卷（`docker compose exec cms-db pg_dump ...` 或卷拷贝）→ 新机 `docker compose up` → 恢复。
- 升级后端：拉新镜像 tag → `docker compose up -d cms-backend`（滚动替换，DB 不受影响）。

### 6.6 反向代理与 HTTPS
- 单机构建：`cms-console`(nginx) 直接对外 80；接入域名后由 nginx 配置 TLS（证书挂载到容器）或前置 Traefik / Caddy 自动签发。
- 控制台域名（如 `cms.qtysoft-ai.com`）与公开官网域名分离，CORS 仅放行该域名。
- 公开官网（Pages / Vercel）**不在 compose 内**，由发布钩子触发独立构建。

### 6.7 常用命令
```bash
# 构建镜像（CI 内）
docker build -t $REGISTRY/cms-backend:latest ./cms-backend
docker build -t $REGISTRY/cms-console:latest  ./cms-admin

# 本地 / 生产起整套
cp .env.example .env && vi .env     # 填密钥
docker compose up -d

# 查看 / 日志 / 停止
docker compose ps
docker compose logs -f cms-backend
docker compose down
```

---

## 7. 安全要点

- 密码 BCrypt 哈希，绝不明文；JWT 短时效、密钥走环境变量。
- RBAC 方法级授权；`/published` 仅接受部署令牌，与登录 JWT 隔离。
- CORS 白名单；生产关闭 Spring Boot actuator / error 详情。
- 参数化查询（JPA 自动防注入）；文案发布前 HTML 转义，防止 XSS。
- 部署令牌可随时在控制台吊销；数据库定期备份（卷 / pg_dump）。
- **镜像不含任何密钥**；`.env` 与证书仅在宿主机 / Secret 管理，不进镜像层与 Git。

---

## 8. 与现有工作流的关系

| 角色 | 改内容方式 | 触发部署 |
| --- | --- | --- |
| 运营 | CMS 控制台 → PostgreSQL（容器内运行）→ 发布 | 发布钩子 → 静态站重建 |
| 开发者 | 改 `src/` + `npm run sync`（本地 `content/*.json` 兜底） | push main → Pages |

两者最终都生成同一份静态 `dist/`。CMS 是生产内容源；源码路径保留用于结构与样式迭代。

---

## 9. 后续演进

- **素材管理**：`cms_media` 启用，控制台上传图片 → 写库 + 对象存储 → 内容引用。
- **多语言**：`cms_documents` 加 `locale` 列，构建按域名/路径出包。
- **富文本**：长文案支持轻量富文本编辑器。
- **预览环境**：发布前先部署 Preview，确认后合入。
- **审计 / 通知**：发布写审计日志，钉钉 / 飞书推送。
- **编排升级**：单机 compose 满足 v1；后期可平移到 Kubernetes（镜像不变，改编排清单）。

---

## 10. 实现步骤（确认后执行，供排期参考）

1. **后端**：Spring Boot 骨架 + PostgreSQL 表结构 + 认证（JWT/BCrypt/RBAC）+ 内容 CRUD + 版本 + 发布钩子；种子管理员；编写 `Dockerfile`。
2. **前端**：Vue3 + Vite + Element Plus + Pinia/Router + Axios；登录、仪表盘、Schema 驱动编辑器、预览、历史、系统（账号管理）；编写 `Dockerfile` + `nginx.conf`。
3. **衔接**：`build.mjs` 增加 `--cms` 模式拉取已发布内容；配置 GitHub Actions `workflow_dispatch` / Vercel 钩子。
4. **模板改造**：把 `src/pages/*.html` 文案替换为 `{{content.*}}` 占位，与 v0.2 模板改造一致。
5. **Docker 化**：`docker-compose.yml` 编排 `cms-db` / `cms-backend` / `cms-console`；`.env.example` 列全配置项；补构建与运维脚本。
6. **文档**：补《CMS 部署与运维手册》《运营使用手册》，更新 TECH §16 已知限制。
