package com.forever.server.setting;

import com.forever.server.auth.Perm;
import com.forever.server.common.ApiResponse;
import com.forever.server.mail.MailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "站点设置", description = "运行参数后台实时调整，需 JWT 认证；修改落库并即时生效，重启不丢")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final SiteConfigService service;
    private final MailService mailService;

    @Perm("setting:list")
    @Operation(summary = "配置项列表", description = "返回全部已知配置项及当前生效值；value 为空表示尚未设置，走 yml 默认值")
    @GetMapping
    public ApiResponse<List<SettingDtos.SettingResponse>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @Perm("setting:update")
    @Operation(summary = "修改配置项", description = "仅支持已登记的配置键；数值型配置须为 >= 0 的整数，保存后立即生效")
    @PutMapping
    public ApiResponse<SettingDtos.SettingResponse> update(
            @Valid @RequestBody SettingDtos.SettingUpdateRequest request) {
        return ApiResponse.ok(service.update(request.key(), request.value()));
    }

    @Perm("setting:update")
    @Operation(summary = "发送测试邮件", description = "用当前 mail.* SMTP 配置向指定地址发一封测试邮件，验证账密与发件人地址可用；SMTP 未配置或发送失败返回 400")
    @PostMapping("/mail/test")
    public ApiResponse<Void> sendTestMail(@Valid @RequestBody SettingDtos.MailTestRequest request) {
        mailService.send(request.to(), "博客邮件配置测试",
                "这是来自博客后台的测试邮件，收到即说明 SMTP 配置可用。");
        return ApiResponse.ok();
    }
}
