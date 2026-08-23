package com.forever.server.setting;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "站点设置", description = "运行参数后台实时调整，需 JWT 认证；修改落库并即时生效，重启不丢")
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final SiteConfigService service;

    @Operation(summary = "配置项列表", description = "返回全部已知配置项及当前生效值；value 为空表示尚未设置，走 yml 默认值")
    @GetMapping
    public ApiResponse<List<SettingDtos.SettingResponse>> list() {
        return ApiResponse.ok(service.listAll());
    }

    @Operation(summary = "修改配置项", description = "仅支持已登记的配置键；数值型配置须为 >= 0 的整数，保存后立即生效")
    @PutMapping
    public ApiResponse<SettingDtos.SettingResponse> update(
            @Valid @RequestBody SettingDtos.SettingUpdateRequest request) {
        return ApiResponse.ok(service.update(request.key(), request.value()));
    }
}
