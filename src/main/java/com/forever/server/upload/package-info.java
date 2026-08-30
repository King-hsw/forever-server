/**
 * 统一上传模块（无状态，内容寻址）：md5 秒传查询、凭证签发、分片直传。
 * key = {md5}.{ext}（桶根直存，内容寻址）——文件状态全部由 RustFS 自持
 * （对象本体 + 分片会话 uploadId/listParts），业务库零记录；
 * 前端与业务数据一律使用返回的公开桶直链，整个模块挂 upload:upload 权限码。
 */
package com.forever.server.upload;
