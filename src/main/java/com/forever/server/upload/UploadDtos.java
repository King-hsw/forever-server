package com.forever.server.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 统一上传 DTO 集合（无状态，内容寻址，查询与签发分离）。
 * 前端编排：算 md5 → check（查秒传）→ 未命中再要凭证（presign / multipart:init）→
 * 直传 → 业务字段填 accessUrl（RustFS 公开桶直链）。key/uploadId 是会话凭据。
 */
public final class UploadDtos {

    private UploadDtos() {
    }

    @Schema(description = "秒传查询申请")
    public record CheckRequest(
            @Schema(description = "文件 MIME 类型，须在白名单内", example = "image/png")
            @NotBlank String contentType,
            @Schema(description = "文件内容 MD5（32 位十六进制小写），作为对象 key 的寻址依据")
            @NotBlank String md5) {
    }

    @Schema(description = "秒传查询结果")
    public record CheckResponse(
            @Schema(description = "公开桶中是否已存在同内容对象") boolean exists,
            @Schema(description = "已存在时返回 RustFS 直链（秒传，直接填业务字段）；不存在为 null") String accessUrl,
            @Schema(description = "须携带的 Content-Type 值") String contentType) {
    }

    @Schema(description = "单文件直传凭证申请（check 未命中后调用）")
    public record PresignRequest(
            @Schema(description = "文件 MIME 类型，须在白名单内", example = "image/png")
            @NotBlank String contentType,
            @Schema(description = "文件内容 MD5（32 位十六进制小写），作为对象 key 的寻址依据")
            @NotBlank String md5) {
    }

    @Schema(description = "分片直传初始化申请（check 未命中后调用）")
    public record MultipartInitRequest(
            @Schema(description = "文件 MIME 类型", example = "video/mp4") @NotBlank String contentType,
            @Schema(description = "文件内容 MD5（32 位十六进制小写）") @NotBlank String md5,
            @Schema(description = "文件总字节数（用于计算分片数与校验上限）", example = "52428800")
            @NotNull @Positive Long sizeBytes) {
    }

    @Schema(description = "分片收尾申请")
    public record MultipartCompleteRequest(
            @Schema(description = "init 返回的对象 key") @NotBlank String key,
            @Schema(description = "init 返回的会话 id") @NotBlank String uploadId) {
    }

    @Schema(description = "单文件直传凭证")
    public record PresignResponse(
            @Schema(description = "对象 key；秒传/续传场景供前端记录") String key,
            @Schema(description = "限时直传 PUT 地址；PUT 时须携带与 contentType 一致的请求头") String uploadUrl,
            @Schema(description = "RustFS 公开桶直链，上传成功后填入业务字段") String accessUrl,
            @Schema(description = "PUT 时须携带的 Content-Type 值") String contentType,
            @Schema(description = "签名有效期（秒）") long expiresIn) {
    }

    @Schema(description = "分片初始化结果")
    public record MultipartInitResponse(
            @Schema(description = "对象 key（断点续传凭据，需持久化）") String key,
            @Schema(description = "分片会话 id（断点续传凭据，需持久化）") String uploadId,
            @Schema(description = "分片大小（字节）") long partSize,
            @Schema(description = "分片总数") int partCount,
            @Schema(description = "各分片直传地址，下标 0 对应分片 1") List<String> partUrls,
            @Schema(description = "RustFS 公开桶直链，全部合并后填入业务字段") String accessUrl,
            @Schema(description = "须携带的 Content-Type 值") String contentType,
            @Schema(description = "签名有效期（秒）") long expiresIn) {
    }

    @Schema(description = "分片收尾结果")
    public record MultipartCompleteResponse(
            @Schema(description = "对象 key") String key,
            @Schema(description = "RustFS 公开桶直链") String accessUrl,
            @Schema(description = "核对后的实际大小（字节）") long sizeBytes) {
    }
}
