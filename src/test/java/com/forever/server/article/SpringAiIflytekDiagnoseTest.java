package com.forever.server.article;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Request;
import okio.Buffer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 讯飞星火 × Spring AI（openai-java）连通性/兼容性诊断测试。
 *
 * <p>用于定位「线上 Spring AI 同样的参数可用，这里却 404 / 'id' is not set」的根因：
 * 打印 Spring AI 实际发出的请求（URL、全部头、完整 body）和接口原始响应，
 * 再让 openai-java SDK 解析，观察它到底在哪一步挂掉。
 *
 * <p>运行（默认参数 = 生产库 sys_site_config 的当前值）：
 * <pre>
 * mvn test -Dtest=SpringAiIflytekDiagnoseTest
 * </pre>
 * <p>换其它 OpenAI 兼容接口对比（比如线上那个能跑的环境）：
 * <pre>
 * mvn test -Dtest=SpringAiIflytekDiagnoseTest \
 *     -Dai.baseUrl=https://xxx/v1 -Dai.apiKey=sk-xxx -Dai.model=gpt-4o-mini
 * </pre>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpringAiIflytekDiagnoseTest {

    /**
     * 默认值 = 当前生产库 sys_site_config 的值（含开发用讯飞 API Key，注意别推到公开仓库；
     * 正式使用请改用 -Dai.apiKey / 环境变量 AI_API_KEY）。
     */
    private static final String BASE_URL = prop("ai.baseUrl", "AI_BASE_URL", "https://spark-api-open.xf-yun.com/v1");
    private static final String API_KEY = prop("ai.apiKey", "AI_API_KEY", "fkkkrkMkvILlzGSLlnJp:jaVLeFpBLMKbzENOybJG");
    private static final String MODEL = prop("ai.model", "AI_MODEL", "lite");
    private static final String PROMPT = "Say ok";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * 对照组：裸 JDK HttpClient 发同样的请求。
     * 证明网络/接口/Key 本身可用（HTTP 200 + 有 content），问题只可能出在 SDK 层。
     */
    @Test
    @Order(1)
    void rawHttpBaseline() throws Exception {
        String url = BASE_URL.replaceAll("/+$", "") + "/chat/completions";
        String body = requestJson();

        System.out.println("== 裸 HTTP 对照 ==\nPOST " + url + "\n" + body);
        HttpResponse<String> resp = HTTP.send(
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + API_KEY)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        System.out.println("HTTP " + resp.statusCode());
        System.out.println(resp.body());

        assertTrue(resp.statusCode() / 100 == 2, "裸 HTTP 调用应成功，实际 HTTP " + resp.statusCode());
        JsonNode text = MAPPER.readTree(resp.body()).at("/choices/0/message/content");
        assertTrue(!text.isMissingNode() && !text.asText("").isBlank(), "响应应含 choices[0].message.content");
    }

    /**
     * 复现生产代码路径：按 {@code AiSummaryService} 原来的方式构建 OpenAiChatModel 并调用。
     * OkHttp 拦截器打印真实请求/原始响应；SDK 解析失败时会把异常和原因直接抛在这里。
     */
    @Test
    @Order(2)
    void springAiModelCall() {
        String[] rawBody = new String[1];

        OpenAiChatModel model = OpenAiChatModel.builder()
                .options(OpenAiChatOptions.builder()
                        .baseUrl(BASE_URL)
                        .apiKey(API_KEY)
                        .model(MODEL)
                        .build())
                .httpClientBuilderCustomizer(b -> b.interceptor(chain -> {
                    Request req = chain.request();
                    System.out.println("== Spring AI 实际发出的请求（配置 model=" + MODEL + "） ==");
                    System.out.println(req.method() + " " + req.url());
                    for (String name : req.headers().names()) {
                        String value = req.headers().get(name);
                        System.out.println("  " + name + ": " + ("Authorization".equals(name) ? "***" : value));
                    }
                    if (req.body() != null) {
                        Buffer buf = new Buffer();
                        req.body().writeTo(buf);
                        System.out.println("  body: " + buf.readUtf8());
                    }
                    okhttp3.Response resp = chain.proceed(req);
                    String respText = resp.peekBody(1 << 20).string();
                    rawBody[0] = respText;
                    System.out.println("== Spring AI 收到的原始响应 ==");
                    System.out.println("HTTP " + resp.code());
                    System.out.println(respText);
                    return resp;
                }))
                .build();

        System.out.println("== Spring AI 解析结果 ==");
        try {
            ChatResponse resp = model.call(new Prompt(PROMPT));
            String text = resp.getResult().getOutput().getText();
            System.out.println("SUCCESS text=" + text);
            assertNotNull(text);
        } catch (Exception e) {
            if (rawBody[0] != null) {
                try {
                    JsonNode root = MAPPER.readTree(rawBody[0]);
                    System.out.println(">>> 响应含 id 字段: " + root.has("id")
                            + " —— openai-java 的 ChatCompletion.id 是必填字段，缺 id 必然校验失败");
                } catch (Exception ignore) {
                    // 响应不是 JSON（比如 WAF 裸 404 页），rawBody 已打印
                }
            }
            System.out.println(">>> SDK 失败: " + e.getClass().getName() + ": " + e.getMessage());
            throw new AssertionError("openai-java 拒绝了该响应，原始报文见上方输出", e);
        }
    }

    /** 与生产 request body 完全同构：model + messages + stream:false */
    private static String requestJson() throws Exception {
        ObjectNode body = MAPPER.createObjectNode()
                .put("model", MODEL)
                .put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", PROMPT);
        return MAPPER.writeValueAsString(body);
    }

    /** 优先 -D 系统属性，其次环境变量，最后默认值（= 当前生产配置） */
    private static String prop(String sysKey, String envKey, String def) {
        String v = System.getProperty(sysKey);
        if (v == null || v.isBlank()) {
            v = System.getenv(envKey);
        }
        return (v == null || v.isBlank()) ? def : v;
    }
}
