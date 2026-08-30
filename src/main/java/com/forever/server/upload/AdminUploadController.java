package com.forever.server.upload;

import com.forever.server.auth.Perm;
import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一上传管理端（无状态）：秒传查询与凭证签发分离、分片直传。
 * 整个模块统一挂 upload:upload 权限码（启动时自动注册，RBAC 分配）——
 * 上传是独立能力，不随登录自动放行。
 * 前端编排：check（查秒传）→ 未命中 → presign / multipart:init（发凭证）→ 直传 → complete。
 */
@Tag(name = "文件上传（通用）", description = "内容寻址直传（md5 秒传）/ 分片断点续传，公开桶直链读取")
@RestController
@RequestMapping("/api/admin/upload")
public class AdminUploadController {

    private final UploadService uploadService;

    public AdminUploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Perm("upload:upload")
    @Operation(summary = "秒传查询", description = """
        上传前先调这里：公开桶中已有同内容（同 md5）对象则 exists=true 并直接返回直链，
        前端跳过上传；不存在才继续调 presign / multipart:init 申请凭证。""")
    @PostMapping("/check")
    public ApiResponse<UploadDtos.CheckResponse> check(
            @Valid @RequestBody UploadDtos.CheckRequest request) {
        return ApiResponse.ok(uploadService.check(request.contentType(), request.md5()));
    }

    @Perm("upload:upload")
    @Operation(summary = "申请单文件直传凭证", description = """
        check 未命中后调用：返回限时 PUT 地址 uploadUrl 与直链 accessUrl。
        前端以 PUT 携带 contentType 请求头直传文件体，成功后把 accessUrl 填入业务字段。""")
    @PostMapping("/presign")
    public ApiResponse<UploadDtos.PresignResponse> presign(
            @Valid @RequestBody UploadDtos.PresignRequest request) {
        return ApiResponse.ok(uploadService.presign(request.contentType(), request.md5()));
    }

    @Perm("upload:upload")
    @Operation(summary = "初始化分片直传", description = """
            check 未命中后调用：返回 uploadId、partSize（8MB）、partCount 与全部分片直传地址。
            非末片须等于 partSize；全部传完后调用 /multipart/complete 收尾。""")
    @PostMapping("/multipart/init")
    public ApiResponse<UploadDtos.MultipartInitResponse> multipartInit(
            @Valid @RequestBody UploadDtos.MultipartInitRequest request) {
        return ApiResponse.ok(uploadService.multipartInit(
                request.contentType(), request.md5(), request.sizeBytes()));
    }

    @Perm("upload:upload")
    @Operation(summary = "分片收尾", description = "服务端 listParts 核对分片连续性与大小后合并为正式对象，返回 RustFS 直链")
    @PostMapping("/multipart/complete")
    public ApiResponse<UploadDtos.MultipartCompleteResponse> multipartComplete(
            @Valid @RequestBody UploadDtos.MultipartCompleteRequest request) {
        return ApiResponse.ok(uploadService.complete(request.key(), request.uploadId()));
    }
}
