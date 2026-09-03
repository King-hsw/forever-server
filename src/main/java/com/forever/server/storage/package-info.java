/**
 * 文件存储模块：头像与动态媒体等上传文件统一存 S3 兼容对象存储（RustFS，
 * AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）。
 * 配置在 yml（blog.storage，见 {@link com.forever.server.storage.StorageProperties}），
 * 客户端随 Bean 一次性构建，改配置 = 改 env 重启。
 * 数据库与前端一律使用 RustFS 直链（{endpoint}/{bucket}/{key}），读取不经应用；
 * 应用只读写对象，不碰桶：建桶与桶策略（公开/私有）由运营在 RustFS 控制台处置。
 */
package com.forever.server.storage;
