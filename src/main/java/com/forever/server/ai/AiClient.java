package com.forever.server.ai;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 大模型调用客户端（第三方）：封装对 OpenAI 兼容接口的 chat 调用，业务无关。
 * 配置（地址/Key/模型）走 yml/env（spring.ai.openai.*），Bean 由 starter 自动装配；
 * API Key 留空即功能关闭，chat() 报「未配置」业务异常。
 * 其他 AI 能力（翻译、续写等）可复用本客户端，各自组装 prompt 即可。
 */
@Slf4j
@Component
public class AiClient {

    private final ObjectProvider<OpenAiChatModel> chatModel;
    private final String apiKey;

    public AiClient(ObjectProvider<OpenAiChatModel> chatModel,
                    @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.chatModel = chatModel;
        this.apiKey = apiKey;
    }

    /**
     * 把 prompt 发给当前配置的模型，返回输出文本。
     */
    public String chat(String prompt) {
        OpenAiChatModel model = chatModel.getIfAvailable();
        if (apiKey == null || apiKey.isBlank() || model == null) {
            throw new BizException(ErrorCode.CONFLICT, "未配置 AI API Key（SPRING_AI_OPENAI_API_KEY）");
        }
        try {
            return model.call(new Prompt(prompt))
                    .getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("ai chat call failed", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "AI 接口调用失败：" + e.getMessage());
        }
    }
}
