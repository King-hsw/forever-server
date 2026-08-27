# forever-server

个人博客后台 REST API。Spring Boot 4 + MyBatis + PostgreSQL + Flyway，按功能分包（article / category / tag / rss / auth / friendlink）。

## 功能

- 文章管理：草稿/发布、slug、标签多对多、分类、软删除、独立页面（type=PAGE，如“关于”）
- 内容格式：正文带 content_format（MARKDOWN/HTML），Core 只存取不渲染
- 机器友好 API：文章响应含 url（基于后台站点设置 site.url 拼接，见 /api/admin/settings）与 readingTime 预估阅读时长
- RSS 订阅：维护订阅源，定时抓取友博文章聚合展示；本站 RSS 仅含 ARTICLE 类型
- 友链申请与审核
- 认证：双 Token（access 2 小时 + refresh 30 天，落库可吊销），登录后访问 `/api/admin/**`；登录成败入审计日志
- 审计日志：后台写操作（POST/PUT/DELETE）自动记录操作人/路径/响应码/IP/耗时，`GET /api/admin/logs` 分页查询
- API 文档：springdoc + Swagger UI
- 日志：logback 滚动文件 + 分环境级别

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

激活方式：`--spring.profiles.active=prod`。

## 常用开发说明

- 数据库变更一律走 Flyway 迁移脚本（`src/main/resources/db/migration/`），禁止手改已合并的脚本
- 本地日志输出到 `logs/forever-server.log`（已在 .gitignore 中）
- 定时任务（RSS 抓取）默认每 6 小时一次，可通过 `blog.rss.fetch-interval-ms` 等配置覆盖
