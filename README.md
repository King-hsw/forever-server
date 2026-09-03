# forever-server

个人博客后台 REST API。Spring Boot 4 + MyBatis + PostgreSQL + Flyway，按功能分包，一个包一个业务模块。

- **技术栈**：JDK 25 / Spring Boot 4 / MyBatis / PostgreSQL 16+ / Flyway / Spring Security（双 Token）/ AWS SDK v2（S3 兼容对象存储）/ springdoc（Swagger UI）
- **接口分层**：`/api/v1/**` 前台公开接口（无需登录）、`/api/admin/**` 后台接口（需登录 + RBAC 权限码）、`/rss` 本站 RSS 输出；文件读取直连 RustFS 公开桶直链，不经应用
- **文档**：启动后访问 <http://localhost:8080/swagger-ui.html>（生产环境默认关闭）

## 模块总览

| 包 | 职责 | 主要表 |
|---|---|---|
| `auth` | 登录认证（双 Token）、RBAC 用户/角色/权限、个人资料 | sys_user / sys_role / sys_permission / sys_auth_token |
| `article` | 文章 CRUD、发布流、归档、独立页面、AI 概要 | article / article_tag |
| `category` | 文章分类 | category |
| `tag` | 文章标签 | tag |
| `comment` | 评论（文章/留言板/动态三种目标复用一套表） | comment |
| `message` | 消息中心：登录账号的站内消息收件箱（单向通知） | message |
| `sensitive` | 评论敏感词库（内存缓存） | sensitive_word |
| `moment` | 动态（朋友圈）：时间线、发布、媒体 | moment |
| `storage` | 文件存储：RustFS（S3 兼容）读写、直传/分片/续传、媒体资产建档、公开桶直链读取 | — |
| `rss` | 友博订阅聚合 + 本站 RSS 输出 | rss_feed / rss_item |
| `friendlink` | 友链申请与审核 | friend_link |
| `search` | 全局搜索（标题/摘要/正文 + 高亮） | search_index |
| `setting` | 站点设置：运行参数后台在线调整，即时生效 | sys_site_config |
| `actionlog` | 审计日志：后台写操作与登录记录 | action_log |
| `common` | 统一响应、异常、分页、slug、工具类 | — |
| `config` | Security / Web MVC / 启动期配置绑定 | — |

迁移脚本：基线 `src/main/resources/db/migration/V1__init.sql`（原 V1–V25 增量脚本的合并净结构）+ 后续 `V2__*.sql` 增量（如 message 表、comment.user_id），启动时 Flyway 自动执行。

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

| `PUT /api/admin/profile/password` | 修改密码（需原密码） |

**RBAC**：用户 → 角色 → 权限码三级，仅内置 ADMIN 角色，其余角色后台按需创建。接口用 `@Perm("article:publish")` 显式声明所需权限码——**未声明一律拒绝（fail-closed）**，裸 `@Perm` 表示仅需登录态。启动时 `PermissionAutoRegistrar` 扫描全部 `@Perm` 注解，幂等补 `sys_permission` 行并授予 ADMIN 角色，新接口无需手工插权限数据。权限集按 uid 内存缓存，角色/用户变更后即时失效。

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
- 落库后发布 `CommentCreatedEvent`：邮件/Web Push 通知（回复通知被回复者、新根评论通知站长，`comment.notify-mail` / `comment.owner-email` 控制）与站内消息（见 message 模块）各自订阅，**通知失败绝不影响评论**
- 管理端删除评论级联删除楼内回复

## 消息中心（message）

站内消息收件箱：收件人是登录账号（sys_user），单向通知（不建会话）。订阅评论创建事件：回复 → 原评论人、根评论 → 站长，自己评自己不写。

| 接口 | 说明 |
|---|---|
| `GET /api/v1/messages` | 本人消息列表（登录态） |
| `GET /api/v1/messages/unread-count` | 未读数 |
| `PUT /api/v1/messages/{id}/read`、`PUT /api/v1/messages/read-all` | 标记已读 |
| `DELETE /api/v1/messages/{id}` | 删除单条（软删） |

要点：`/api/v1/**` 默认放行，各端点自行校验登录态（匿名 401）；收件箱是记录，不受 `comment.notify-mail` 开关控制。

## 动态（moment）

| 接口 | 说明 |
|---|---|
| `GET /api/v1/moments` | 公开时间线（可按 userUid 过滤；canDelete 按访问者计算） |
| `GET /api/v1/moments/geocode` | 高德逆地理（lat/lng → 城市区县文本，未配 key 静默返回空） |
| `POST /api/admin/moments` | 发布（`moment:post`），内容/图/音/视频至少一项，图片 ≤9 张 |
| `DELETE /api/admin/moments/{id}` | 删除（作者本人或 ADMIN；级联删评论） |

统一上传接口（upload 模块，场景化复用，详见「文件存储」）：

| 接口 | 说明 |
|---|---|
| `POST /api/admin/upload/check` | 秒传查询：公开桶已有同 md5 内容 → 直接返回直链，跳过上传 |
| `POST /api/admin/upload/presign` | 单文件直传凭证（check 未命中后调用）：限时 PUT 地址 + 直链 |
| `POST /api/admin/upload/multipart/init` | 分片初始化：8MB/片，返回 uploadId 与全部分片直传地址 |
| `POST /api/admin/upload/multipart/complete` | 分片收尾：核对分片连续性/大小后合并 |

> 以上接口统一需要 `upload:upload` 权限码（启动时自动注册，RBAC 分配）——上传是独立模块能力，不随登录自动放行。

要点：媒体白名单（图片 jpg/png/webp/gif ≤5MB、音频 mp3/m4a/wav ≤20MB、视频 mp4/webm ≤100MB），Content-Type 校验与扩展名映射共用一张表；媒体 JSON（images/audio/video）随动态存取；地点文本经高德逆地理（`AmapService`，key 在 yml `blog.moments.amap.key`，未配静默降级）；时间线批量组装作者/评论数避免 N+1。

## 文件存储（storage）

上传文件（头像、动态媒体）统一写入 S3 兼容对象存储 **RustFS**（官方推荐 AWS SDK v2 接入，path-style + us-east-1）。应用只读写对象，**不建桶、不改桶策略**——桶需预先在 RustFS 控制台建好并设为公开读。数据库与前端一律存/用 **RustFS 直链**（`{endpoint}/{bucket}/{key}`），读取不经应用：

- 缓存策略不在代码里设置——需要 CDN 加速/缓存控制时，在 CDN 或反向代理层统一配置
- `blog.storage.endpoint` 必须是**浏览器可达的地址**（公网域名或反代后的地址）；换地址 = 改 `BLOG_STORAGE_ENDPOINT` 环境变量并重启 + 历史直链失效需一次性迁移，这是直链模式的已知代价
- 将来需要权限控制的文件走**独立隐私桶**（匿名不可读，读取由业务接口现签预签名 URL）

**存储配置在 yml/环境变量**（`blog.storage`：`endpoint` / `access-key` / `secret-key` / `bucket` / `presign-ttl`，生产见 `application-prod.yml` 的 `BLOG_STORAGE_*` 环境变量，本地在 `local/application-local.yml`），S3 客户端随 Bean 一次性构建，改配置 = 改 env 重启。缺项或格式错误启动即失败（fail fast）；应用不对桶做任何管理操作。

**内容寻址（无状态，无文件表）**：对象 key = `{md5}.{ext}`（桶根直存，前端算好 md5 随申请带上）——**key 本身就是内容的指纹**，秒传查询就是对这个地址发一次 HEAD：命中即说明同内容已上传，直接返回直链。查询（check）与凭证签发（presign/init）是两个独立接口，由前端编排。文件状态（对象、分片会话）全部由 RustFS 自持，业务库不记录任何文件信息，发布时仅校验引用为合法直链。代价：无归属校验（持有直链即持有文件）、无台账，中断的分片会话与未引用对象由运营侧扫桶处置。

**直传与分片续传**（大文件/弱网场景）：

1. 前端本地算文件 md5 → `POST /api/admin/upload/check` → `exists=true` 即秒传（直接拿直链，跳过上传）
2. 未命中 → `POST /api/admin/upload/presign`（单文件）或 `multipart/init`（分片）拿凭证 → PUT 文件体（Content-Type 头与凭证一致，参与签名）→ 把 `accessUrl` 填入业务字段
3. 分片：`multipart/init`（8MB/片）→ 逐片 PUT 到 `partUrls` → `multipart/complete` 收尾（中断即作废重传）

本地起一个 RustFS 试用（S3 API 9000 / 控制台 9001，默认账密 `rustfsadmin`/`rustfsadmin`）：

```bash
docker run -d --name rustfs -p 9000:9000 -p 9001:9001 rustfs/rustfs:latest
```

然后在 `local/application-local.yml` 的 `blog.storage` 填入 endpoint `http://localhost:9000`、账密 `rustfsadmin` / `rustfsadmin`、桶 `forever`（模板已预置），重启应用即生效。桶 `forever` 需自行在控制台 `http://localhost:9001` 创建并设为公开读（匿名可读），应用不再代劳。

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

`GET /api/v1/search?keyword=`：按标题/摘要/正文模糊搜索已发布文章（pg_trgm GIN 索引），返回标题与摘要片段的 `<em>` 高亮（先在原文定位命中、再分段转义后插 `<em>`，前端可安全 v-html；片段来源：正文第一命中 > 摘要第一命中 > 开头兜底）。关键词超 100 字截断，空关键词返回空页。每次搜索记一条 info 日志（关键词 + 命中数），便于了解访客关注点。

## 站点设置（setting）

`GET /api/admin/settings`（`setting:list`）列出全部可调参数与中文说明，`PUT /api/admin/settings`（`setting:update`）按 key 更新（留空即清除、恢复默认值）：白名单 + 类型校验（布尔只收 true/false、邮箱/URL/日期格式、数值范围），落库 + 内存缓存双写，**即时生效、重启不丢**。`POST /api/admin/settings/mail/test`（`setting:update`）用当前 `mail.*` SMTP 配置向指定地址发测试邮件，验证账密与发件人可用。参数分组：

- **评论**：`comment.post-interval-seconds`（同 IP 发评间隔）、`comment.auto-approve`（直接过审）、`comment.notify-mail`（邮件通知开关）、`comment.owner-email` / `comment.from-email`
- **邮件 SMTP**：`mail.host`（必填，留空 = 未配置不发信）、`mail.port`（默认 465）、`mail.username` / `mail.password`、`mail.ssl`（默认 true；false 走 587 STARTTLS）
- **站点**：`site.url`（文章前台链接与 RSS 用）、`site.name`、`site.birth-date`（页脚运行时长）、`board.title` / `board.summary`
- **AI 概要**：`ai.summary-enabled`、`ai.api-key`、`ai.base-url`、`ai.model`

> 密钥类配置（ai.api-key / mail.password）的更新日志自动脱敏为 `***`，不会明文写入日志文件。文件存储（storage）不在站点设置里，配置见「文件存储（storage）」节的 yml/环境变量。

## 审计日志（actionlog）

后台写操作自动审计：拦截器对 `/api/admin/**` 下的 POST/PUT/DELETE/PATCH 记录操作人 / 方法 / 路径 / 响应码 / IP / 耗时，无需逐接口打注解（GET 不记）；登录成败（含尝试的用户名）由 AuthService 显式记录。`GET /api/admin/logs`（`log:list`）分页查询，支持按用户名/路径筛选。审计写入失败只记运行日志，绝不影响业务请求。

## 公共与配置（common / config）

- `common`：`ApiResponse` 统一响应（code/message/data）、`BizException` + `ErrorCode` 业务异常、`GlobalExceptionHandler`（业务异常 warn 一行不打堆栈，兜底异常 error 记堆栈但响应不外泄）、`PageResult`/`PageParams` 分页、`SlugGenerator` slug 生成、`Strings`/`Web` 工具（URL 校验、Gravatar、客户端 IP 等）
- `config`：`SecurityConfig`（无状态会话、Bearer 过滤器按 RBAC 权限码构建 authority、CORS 放开、401/403 统一 JSON）、`WebConfig`（鉴权 + 审计拦截器注册于 `/api/admin/**`）、`BlogProperties`（`blog.*` 启动期配置）、`OpenApiConfig`

## 日志

- **级别约定**：`info` 记管理端写操作结果与安全事件（登录成败、改密、登出、权限变更、配置变更）；`debug` 记公开端高频行为（直传预签、高德失败原因等）与开发排查；`warn` 记预期内失败（校验拒绝、外部服务失败、限流）；`error` 记需人工介入的异常（存储读写失败等）。密钥类配置日志自动脱敏。
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
| `BLOG_STORAGE_ENDPOINT` / `BLOG_STORAGE_ACCESS_KEY` / `BLOG_STORAGE_SECRET_KEY` / `BLOG_STORAGE_BUCKET` | 文件存储（RustFS S3 兼容），必填；endpoint 为浏览器可达的 S3 公网地址 |
| `BLOG_STORAGE_PRESIGN_TTL` | 预签名 URL 有效期，可选，默认 `15m` |
| `BLOG_MOMENTS_AMAP_KEY` | 高德 Web Service key（动态页「获取当前位置」逆地理），可选，留空 = 功能关闭 |

激活方式：`--spring.profiles.active=prod`。

## 常用开发说明

- 数据库变更一律走 Flyway 迁移脚本（`src/main/resources/db/migration/`），禁止手改已合并的脚本
- 运行参数（评论策略、站点地址、AI 等）一律走后台「站点设置」，yml 不承载运行参数；`blog.*` 仅保留启动期配置（初始管理员、文件存储）
- 后台新接口必须声明 `@Perm`（裸 `@Perm` = 仅需登录），否则该接口一律 403；权限码由启动扫描自动登记
- 本地日志输出到 `logs/forever-server.log`（已在 .gitignore 中）
- 定时任务（RSS 抓取）默认每 6 小时一次，可通过 `blog.rss.fetch-interval-ms` 等配置覆盖
