package com.forever.server.setting;

import com.forever.server.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点公开信息。目前只有建站日期，前台页脚据此计算运行时长。
 */
@Tag(name = "站点·公开接口", description = "访客可读的站点信息，无需登录")
@RestController
public class SiteInfoController {

    private final SiteConfigService siteConfig;

    public SiteInfoController(SiteConfigService siteConfig) {
        this.siteConfig = siteConfig;
    }

    public record SiteInfo(String birthDate) {
    }

    @Operation(summary = "站点信息", description = "birthDate 来自后台站点设置 site.birth-date，未设置为 null")
    @GetMapping("/api/v1/site")
    public ApiResponse<SiteInfo> info() {
        return ApiResponse.ok(new SiteInfo(siteConfig.birthDate()));
    }
}
