/**
 * 文件存储模块：头像与动态媒体等上传文件统一存 S3 兼容对象存储（RustFS，
 * AWS SDK v2 接入，见 docs.rustfs.com/zh/developer/sdk/java）。
 * 生效配置由 {@link com.forever.server.storage.StorageSettings} 解析——
 * 统一在后台「站点设置」（sys_site_config 的 storage.*）在线配置，修改即时生效无需重启；
 * 未配置项回落内置默认值（见 StorageProperties）。
 * 数据库存 RustFS 公开桶直链（{endpoint}/{bucket}/{key}），由
 * 对象均在公开桶，前端使用 RustFS 直链（{endpoint}/{bucket}/{key}）直接读取。
 */
package com.forever.server.storage;
