/**
 * 文件存储模块：头像与动态媒体等上传文件统一存 S3 兼容对象存储（RustFS，
 * AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）。
 * 生效配置由 {@link com.forever.server.storage.StorageSettings} 解析——
 * 站点设置（sys_site_config 的 storage.*）优先，yml/环境变量兜底，后台修改即时生效无需重启。
 * 数据库一律存 /uploads/{相对路径} 形式的稳定地址，由
 * {@link com.forever.server.storage.UploadsController} 302 到预签名 URL 或公开读固定直链。
 */
package com.forever.server.storage;
