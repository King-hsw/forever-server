# forever-server

个人博客后台 REST API。Spring Boot 4 + MyBatis + PostgreSQL + Flyway，按功能分包，一个包一个业务模块。

- **技术栈**：JDK 25 / Spring Boot 4 / MyBatis / PostgreSQL 16+ / Flyway / Spring Security（双 Token）/ AWS SDK v2（S3 兼容对象存储）/ springdoc（Swagger UI）
- **接口分层**：`/api/v1/**` 前台公开接口（无需登录）、`/api/admin/**` 后台接口（需登录 + RBAC 权限码）、`/uploads/**` 文件访问（302 到对象存储）、`/rss` 本站 RSS 输出
- **文档**：启动后访问 <http://localhost:8080/swagger-ui.html>（生产环境默认关闭）

## 模块总览

| 包 | 职责 | 主要表 |
|---|---|---|
| `auth` | 登录认证（双 Token）、RBAC 用户/角色/权限、个人资料 | sys_user / sys_role / sys_permission / sys_auth_token |
| `article` | 文章 CRUD、发布流、归档、独立页面、AI 概要 | article / article_tag |
| `category` | 文章分类 | category |
| `tag` | 文章标签 | tag |
| `comment` | 评论（文章/留言板/动态三种目标复用一套表） | comment |
| `sensitive` | 评论敏感词库（内存缓存） | sensitive_word |
| `moment` | 动态（朋友圈）：时间线、发布、点赞、媒体 | moment / moment_like |
| `storage` | 文件存储：RustFS（S3 兼容）读写、直传收口、/uploads 302 | — |
| `rss` | 友博订阅聚合 + 本站 RSS 输出 | rss_feed / rss_item |
| `friendlink` | 友链申请与审核 | friend_link |
| `search` | 全局搜索（标题/摘要/正文 + 高亮） | search_index |
| `setting` | 站点设置：运行参数后台在线调整，即时生效 | sys_site_config |
| `actionlog` | 审计日志：后台写操作与登录记录 | action_log |
| `common` | 统一响应、异常、分页、slug、工具类 | — |
| `config` | Security / Web MVC / 启动期配置绑定 | — |

迁移脚本见 `src/main/resources/db/migration/`（V1–V21），启动时 Flyway 自动执行。

---

## 认证与权限（auth）

**登录认证**：双 Token 方案——access（2 小时，随请求 `Authorization: Bearer` 提交）+ refresh（30 天，仅用于换新）。均为随机不透明串，库中只存 SHA-256，删号/禁用/清库后旧 token 立即失效（区别于自包含 JWT）。refresh 换新采用轮换（旧行作废、签发新对）防重放。

| 接口 | 说明 |
|---|---|
| `POST /api/auth/login` | 登录，签发令牌对 |
| `POST /api/auth/refresh` | refresh 换发新令牌对 |
| `POST /api/auth/logout` | 登出，吊销整个会话 |
| `GET /api/admin/me` | 当前身份、角色与权限码（仅需登录） |
| `GET/PUT /api/admin/profile` | 个人资料（昵称/邮箱/主页） |
| `POST/DELETE /api/admin/profile/avatar` | 头像上传（≤2MB，jpg/png/webp，Gravatar 兜底）/ 删除 |
| `PUT /api/admin/profile/password` | 修改密码（需原密码） |

**RBAC**：用户 → 角色 → 权限码三级，内置 ADMIN / USER 角色。接口用 `@Perm("article:publish")` 显式声明所需权限码——**未声明一律拒绝（fail-closed）**，裸 `@Perm` 表示仅需登录态。启动时 `PermissionAutoRegistrar` 扫描全部 `@Perm` 注解，幂等补 `sys_permission` 行并授予 ADMIN 角色，新接口无需手工插权限数据。权限集按 uid 内存缓存，角色/用户变更后即时失效。

| 接口 | 权限码 |
|---|---|
| `GET /api/admin/users`、`POST /api/admin/users` | `rbac:user:list` / `rbac:user:create` |
| `PUT /api/admin/users/{id}/status`（启停用） | `rbac:user:status` |
| `PUT /api/admin/users/{id}/password`（重置密码） | `rbac:user:password` |
| `PUT /api/admin/users/{id}/roles`（分配角色） | `rbac:user:roles` |
| `GET /api/admin/roles`、`POST`、`DELETE /{id}` | `rbac:role:list` / `rbac:role:create` / `rbac:role:delete` |
| `PUT /api/admin/roles/{id}/permissions`（权限矩阵，覆盖式设置） | `rbac:role:permissions` |
| `GET /api/admin/permissions` | `rbac:permission:list` |

登录成败、改密、登出、权限变更等安全事件均记录运行日志，后台写操作另入审计日志（见 actionlog 模块）。

## 文章（article）

| 接口 | 说明 |
|---|---|
| `GET /api/v1/articles` | 已发布文章列表（keyword / categoryId / tagId 筛选） |
| `GET /api/v1/articles/archive` | 归档（按发布时间倒序） |
| `GET /api/v1/articles/{slug}` | 详情（slug 访问，浏览量 +1） |
| `GET/POST /api/admin/articles`、`GET/PUT/DELETE /{id}` | 管理端 CRUD（`article:list/read/create/update/delete`） |
| `PUT /{id}/publish`、`PUT /{id}/unpublish` | 发布 / 下线（`article:publish` / `article:unpublish`） |
| `POST /{id}/ai-summary` | AI 生成概要（`article:ai-summary`） |

要点：

- 草稿/发布两态 + 软删除（关联与数据保留）；slug 全局唯一，未指定时从标题提取语义化 slug（英文数字词段，SEO 友好），冲突追加随机后缀重试
- `type=ARTICLE` 博文 / `type=PAGE` 独立页面（如「关于」）；正文带 `content_format`（MARKDOWN/HTML），服务端只存取不渲染
- 机器友好 API：响应含 `url`（按站点设置 `site.url` 拼接的前台链接）与 `readingTime`（预估阅读时长）
- 标签多对多随文章保存整体重建；分类/标签引用在写入前校验
- AI 概要调 OpenAI 兼容接口（配置在站点设置，正文超 8000 字截断），生成结果写回 `article.summary`

## 分类（category）与标签（tag）

- 公开：`GET /api/v1/categories`、`GET /api/v1/tags`（携带已发布文章数）
- 管理：`/api/admin/categories`、`/api/admin/tags` CRUD（`category:*` / `tag:*`）

要点：名称唯一；分类 slug 缺省随机生成；分类删除前校验无文章引用（防悬挂），标签删除先清文章-标签关联再删标签。

## 评论与留言板（comment）+ 敏感词（sensitive）

一套 `comment` 表支撑三种目标（`target_type`）：文章评论、留言板（挂固定 `target_id=0`）、动态评论，两层楼结构（根评论倒序、楼内回复正序，楼内回复可展示被回复内容引用）。

| 接口 | 说明 |
|---|---|
| `GET /api/v1/articles/{id}/comments`、`POST /api/v1/comments` | 文章评论读/写（公开） |
| `GET /api/v1/board`、`GET/POST /api/v1/board/messages` | 留言板信息与留言读/写（公开） |
| `GET/POST /api/v1/moments/{id}/comments` | 动态评论读/写（公开，见 moment 模块） |
| `GET /api/admin/comments`、`PUT /{id}/approve`、`/{id}/reject`、`DELETE /{id}` | 审核与维护（`comment:list/approve/reject/delete`） |

要点：

- 游客发评需昵称 + 邮箱（头像走 Gravatar）；**登录用户在动态下自动以 sys_user 资料发言，邮箱可为空**
- 写入前敏感词替换（`sensitive` 模块，词库内存缓存即时生效，`/api/admin/sensitive-words` 维护，未配替换字默认打码 `***`）
- 是否直接过审（`comment.auto-approve`）、同 IP 发评最小间隔（`comment.post-interval-seconds`，0 不限流）由站点设置控制，默认直接过审、间隔 10 秒
- 落库后触发邮件通知（回复通知被回复者、新根评论通知站长，`comment.notify-mail` / `comment.owner-email`），**通知失败绝不影响评论**
- 管理端删除评论级联删除楼内回复

## 动态（moment）

| 接口 | 说明 |
|---|---|
| `GET /api/v1/moments` | 公开时间线（可按 userUid 过滤；liked / canDelete 按访问者计算） |
| `GET /api/v1/moments/geocode` | 高德逆地理（lat/lng → 城市区县文本，未配 key 静默返回空） |
| `POST /api/admin/moments` | 发布（`moment:post`），内容/图/音/视频至少一项，图片 ≤9 张 |
| `DELETE /api/admin/moments/{id}` | 删除（作者本人或 ADMIN；级联删点赞与评论） |
| `POST/DELETE /api/admin/moments/{id}/like` | 点赞 / 取消（仅需登录，幂等） |
| `POST /api/admin/upload` | 媒体服务端中转上传（≤120MB 单文件） |
| `POST /api/admin/upload/presign` | 直传预签名（大文件绕过服务端，见 storage 模块） |

要点：媒体白名单（图片 jpg/png/webp/gif ≤5MB、音频 mp3/m4a/wav ≤20MB、视频 mp4/webm ≤100MB），Content-Type 校验与扩展名映射共用一张表；媒体 JSON（images/audio/video）随动态存取；地点文本经高德逆地理（`moments.amapKey`）；时间线批量组装作者/点赞数/评论数避免 N+1。

## 文件存储（storage）

上传文件（头像、动态媒体）统一写入 S3 兼容对象存储 **RustFS**（官方推荐 AWS SDK v2 接入，path-style + us-east-1，桶缺失自动创建）。数据库恒存 `/uploads/...` 稳定地址，访问时 302 到实际对象：

- **私有模式**：302 跳预签名下载 URL（`storage.presign-ttl`，默认 15 分钟）
- **公开读模式**（`storage.public-read`，小带宽服务器建议开启）：自动安装匿名只读桶策略（仅 `moment/` 与 `avatar/` 前缀，`tmp/` 与列举保持私有），302 跳固定直链，对象带缓存策略——moment 媒体 `immutable` 强缓存一年、avatar 每次再校验，每个访客每个文件只拉一次；URL 恒定，日后接 CDN 零改造

**存储配置在后台「站点设置」在线完成**（`storage.endpoint` / `access-key` / `secret-key` / `bucket` / `presign-ttl` / `tmp-expire-days` / `public-read`），保存即时生效、重启不丢，换对象存储无需重新部署；`blog.storage.*`（yml/环境变量）仅作未在后台配置时的兜底默认值。S3 客户端按配置元组惰性构建，配置变更后自动重建并幂等预配桶（建桶、`tmp/` 生命周期、公开读策略）。连接信息不完整时应用正常启动，仅上传/下载报「配置不完整」。

**动态媒体直传**（可选，大文件如 100MB 视频绕过服务端中转）：

1. `POST /api/admin/upload/presign`（body: `{"contentType":"image/png"}`）→ 限时 PUT 地址 `url` + 对象 `key`（`tmp/{yyyy/MM}/{uuid}.{ext}`）
2. 前端 `PUT` 文件体到 `url`，请求头 `Content-Type` 与预签时一致（参与签名，必须原样）
3. 发布动态时把 `key` 填入 `images` / `audio` / `video` 字段，服务端 HeadObject 复核类型与大小后 CopyObject 收口为 `/uploads/moment/...` 正式对象并删除暂存（key 格式正则校验，防伪造收口任意对象）
4. 未发布的暂存对象由桶生命周期规则（`tmp/` 前缀，`storage.tmp-expire-days` 默认 1 天）自动回收，配置生效后首次使用时幂等安装

本地起一个 RustFS 试用（S3 API 9000 / 控制台 9001，默认账密 `rustfsadmin`/`rustfsadmin`）：

```bash
docker run -d --name rustfs -p 9000:9000 -p 9001:9001 rustfs/rustfs:latest
```

然后启动后登录后台「站点设置」填入 `http://localhost:9000` / `rustfsadmin` / `rustfsadmin` / `forever`（或在 `local/application-local.yml` 的 `blog.storage.*` 配置同一组值，作为兜底默认）。

> 前端直连预签名地址（跨域 XHR）时需在 RustFS 控制台为桶开启 CORS；`<img>`/`<video>` 标签加载不受影响。

## RSS（rss）

| 接口 | 说明 |
|---|---|
| `GET /api/v1/rss/items` | 聚合的友博文章流 |
| `GET /api/v1/rss/feeds` | 启用中的订阅源列表 |
| `GET /rss` | 本站 RSS 输出（仅 ARTICLE 类型） |
| `/api/admin/rss/feeds` CRUD + `POST /{id}/refresh` | 订阅源维护与手动刷新（`rss:*`） |

要点：JDK HttpClient 拉取 + Rome 解析（兼容 RSS 2.0 / Atom）；条目按 (feed_id, link) 幂等写入，每源每次最多 50 条、不回灌历史旧文；抓取失败记 `last_error` 不影响其他源。定时任务默认启动 1 分钟后首抓、之后每 6 小时一次（`blog.rss.initial-delay-ms` / `fetch-interval-ms` 可覆盖）；创建源后立即首抓一次。

## 友链（friendlink）

- 公开：`GET /api/v1/friend-links`（仅审核通过）、`POST /api/v1/friend-links/apply`（访客申请）
- 管理：`/api/admin/friend-links` CRUD + `POST /{id}/approve` / `/{id}/reject`（`friend-link:*`）

要点：站点地址 http(s) 校验 + 去尾斜杠归一去重；申请默认 PENDING，审核通过 / 驳回（可填驳回原因）；管理端直接创建即 APPROVED。

## 全局搜索（search）

`GET /api/v1/search?keyword=`：按标题/摘要/正文模糊搜索已发布文章（V14 搜索索引表），返回标题与摘要片段的 `<em>` 高亮（先在原文定位命中、再分段转义后插 `<em>`，前端可安全 v-html；片段来源：正文第一命中 > 摘要第一命中 > 开头兜底）。关键词超 100 字截断，空关键词返回空页。每次搜索记一条 info 日志（关键词 + 命中数），便于了解访客关注点。

## 站点设置（setting）

`GET /api/admin/settings`（`setting:list`）列出全部可调参数与中文说明，`PUT /api/admin/settings`（`setting:update`）按 key 更新：白名单 + 类型校验（布尔只收 true/false、邮箱/URL/日期格式、数值范围），落库 + 内存缓存双写，**即时生效、重启不丢**。参数分组：

- **评论**：`comment.post-interval-seconds`（同 IP 发评间隔）、`comment.auto-approve`（直接过审）、`comment.notify-mail`（邮件通知开关）、`comment.owner-email` / `comment.from-email`
- **站点**：`site.url`（文章前台链接与 RSS 用）、`site.name`、`site.birth-date`（页脚运行时长）、`board.title` / `board.summary`
- **AI 概要**：`ai.summary-enabled`、`ai.api-key`、`ai.base-url`、`ai.model`
- **动态**：`moments.amapKey`（高德逆地理）
- **存储**：`storage.endpoint` / `access-key` / `secret-key` / `bucket` / `presign-ttl` / `tmp-expire-days` / `public-read`（见 storage 模块）

> 密钥类配置（access-key / secret-key / api-key）的更新日志自动脱敏为 `***`，不会明文写入日志文件。

## 审计日志（actionlog）

后台写操作自动审计：拦截器对 `/api/admin/**` 下的 POST/PUT/DELETE/PATCH 记录操作人 / 方法 / 路径 / 响应码 / IP / 耗时，无需逐接口打注解（GET 不记）；登录成败（含尝试的用户名）由 AuthService 显式记录。`GET /api/admin/logs`（`log:list`）分页查询，支持按用户名/路径筛选。审计写入失败只记运行日志，绝不影响业务请求。

## 公共与配置（common / config）

- `common`：`ApiResponse` 统一响应（code/message/data）、`BizException` + `ErrorCode` 业务异常、`GlobalExceptionHandler`（业务异常 warn 一行不打堆栈，兜底异常 error 记堆栈但响应不外泄）、`PageResult`/`PageParams` 分页、`SlugGenerator` slug 生成、`Strings`/`Web` 工具（URL 校验、Gravatar、客户端 IP 等）
- `config`：`SecurityConfig`（无状态会话、Bearer 过滤器按 RBAC 权限码构建 authority、CORS 放开、401/403 统一 JSON）、`WebConfig`（鉴权 + 审计拦截器注册于 `/api/admin/**`）、`BlogProperties`（`blog.*` 启动期配置）、`OpenApiConfig`

## 日志

- **级别约定**：`info` 记管理端写操作结果与安全事件（登录成败、改密、登出、权限变更、配置变更）；`debug` 记公开端高频行为（点赞、直传预签、高德失败原因等）与开发排查；`warn` 记预期内失败（校验拒绝、外部服务失败、限流）；`error` 记需人工介入的异常（存储读写失败等）。密钥类配置日志自动脱敏。
- **输出**：开发（dev/default）控制台彩色输出、本项目代码 DEBUG 级；生产（prod）文件 INFO 起、控制台仅 WARN 起。文件按天 + 20MB 滚动，保留 14 天、总量上限 1GB（`logs/forever-server.log`，路径可用 `logging.file.name` 覆盖）。

---

## 环境要求

- JDK 25+
- PostgreSQL 16+
- Maven 3.9+（或直接用 IDEA）

## 首次启动（必读）

应用启动依赖本地私密配置，**仓库中不包含**，需手动创建：

```bash
# 1. 复制模板并填入真实值
cp local/application-local.yml.example local/application-local.yml
# 然后编辑 local/application-local.yml：
#   - 数据库连接账密
#   - blog.admin.username/password（仅 sys_user 为空时用于初始化管理员）

# 2. 建库（Flyway 迁移会在启动时自动建表）
createdb forever   # 或 psql: CREATE DATABASE forever;

# 3. 启动
mvn spring-boot:run
```

启动成功后：

- Swagger UI：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>

> ⚠️ `local/` 目录已被 `.gitignore` 排除，真实配置严禁以任何形式提交。

## 生产部署

生产环境通过环境变量覆盖配置（见 `src/main/resources/application-prod.yml`）：

| 环境变量 | 说明 |
|---|---|
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | 数据源 |
| `BLOG_ADMIN_PASSWORD` | 初始管理员密码（仅首次启动建号用） |
| `BLOG_STORAGE_ENDPOINT` / `BLOG_STORAGE_ACCESS_KEY` / `BLOG_STORAGE_SECRET_KEY` / `BLOG_STORAGE_BUCKET` | 对象存储连接信息（也可启动后在后台「站点设置」配置，站点设置优先） |

激活方式：`--spring.profiles.active=prod`。

## 常用开发说明

- 数据库变更一律走 Flyway 迁移脚本（`src/main/resources/db/migration/`），禁止手改已合并的脚本
- 运行参数（评论策略、站点地址、存储、AI 等）一律走后台「站点设置」，不新增 yml 配置；`blog.*` 仅保留启动期必要项与存储兜底默认值
- 后台新接口必须声明 `@Perm`（裸 `@Perm` = 仅需登录），否则该接口一律 403；权限码由启动扫描自动登记
- 本地日志输出到 `logs/forever-server.log`（已在 .gitignore 中）
- 定时任务（RSS 抓取）默认每 6 小时一次，可通过 `blog.rss.fetch-interval-ms` 等配置覆盖
