/**
 * 文件存储模块：头像与动态媒体等上传文件统一存 S3 兼容对象存储（RustFS，
 * AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）。
 * 生效配置由 {@link com.forever.server.storage.StorageSettings} 解析——
 * 统一在后台「站点设置」（sys_site_config 的 storage.*）在线配置，修改即时生效无需重启；
 * 未配置项回落内置默认值（见 StorageProperties）。
 * 数据库与前端一律使用 RustFS 直链（{endpoint}/{bucket}/{key}），读取不经应用；
 * 应用只读写对象，建桶与桶策略（公开/私有）由运营在 RustFS 控制台处置。
 */
package com.forever.server.storage;
