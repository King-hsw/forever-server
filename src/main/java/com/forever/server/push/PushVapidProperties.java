package com.forever.server.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web Push VAPID 配置：密钥对与联系人，属私密信息，实际值放 local/application-local.yml 或环境变量，
 * 不入 git。生成方式（任选其一）：
 * <ul>
 *   <li>npx web-push generate-vapid-keys</li>
 *   <li>web-push 库 CLI：java -cp web-push-5.1.2.jar nl.martijndwars.webpush.cli.Cli generate-key</li>
 * </ul>
 * 三项任一缺失即推送功能关闭（vapid 接口与发送均报错），禁止运行时自动生成——
 * 重启换钥会导致全量订阅失效。
 *
 * @param publicKey  VAPID 应用服务器密钥对公钥（Base64URL，65 字节 P-256 点），下发给前端订阅用
 * @param privateKey VAPID 私钥（Base64URL，32 字节），JWT 签名用
 * @param subject    发起方联系方式（mailto:you@example.com），推送服务拒绝通知时联系用
 */
@ConfigurationProperties(prefix = "blog.push.vapid")
public record PushVapidProperties(String publicKey, String privateKey, String subject) {

    /** 是否已完整配置（三项齐备），未配置 = 推送功能关闭 */
    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && subject != null && !subject.isBlank();
    }
}
