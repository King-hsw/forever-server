package com.forever.server.ai;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.setting.SiteConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 大模型调用客户端（第三方）：封装对 OpenAI 兼容接口的 chat 调用，业务无关。
 * 配置（地址/Key/模型）来自站点设置，每次调用按当前配置构建客户端，运行时修改即时生效；
 * 其他 AI 能力（翻译、续写等）可复用本客户端，各自组装 prompt 即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final SiteConfigService siteConfig;

    /**
     * 把 prompt 发给当前配置的模型，返回输出文本。
     */
    public String chat(String prompt) {
        try {
            // baseUrl/apiKey 必须写在 options 上：OpenAiChatModel 按 options 构建同步+异步客户端，
            // 只塞自建 client 时 options 缺 apiKey 会在 build() 抛 credential 缺失
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(siteConfig.aiBaseUrl())
                            .apiKey(siteConfig.aiApiKey())
                            .model(siteConfig.aiModel())
                            .build())
                    .build();
            return model.call(new Prompt(prompt))
                    .getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("ai chat call failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 接口调用失败：" + e.getMessage());
        }
    }
}
