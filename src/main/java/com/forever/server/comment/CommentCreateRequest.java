package com.forever.server.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "发表评论请求（访客，无需登录）")
public record CommentCreateRequest(
        @Schema(description = "文章 id", example = "1")
        Long articleId,
        @Schema(description = "被回复的评论 id；发根评论不传")
        Long parentId,
        @Schema(description = "昵称", example = "路人甲")
        @NotBlank(message = "昵称不能为空") @Size(max = 50) String nickname,
        @Schema(description = "邮箱，不公开展示；用于生成头像和接收回复通知", example = "someone@example.com")
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
        @Schema(description = "个人主页，选填；昵称点击跳转", example = "https://example.com")
        @Size(max = 200) String site,
        @Schema(description = "内容", example = "写得太好了！")
        @NotBlank(message = "内容不能为空") @Size(max = 500, message = "评论最多 500 字") String content) {
}
