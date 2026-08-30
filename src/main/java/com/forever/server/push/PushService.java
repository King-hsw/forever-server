package com.forever.server.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Web Push 业务：订阅存储（PG）、推送下发与送达回执。
 * 加密与 VAPID 签名由 web-push 库完成（aes128gcm 编码 + JWT），VAPID 密钥见 {@link PushVapidProperties}。
 * 发送时 404/410 表示订阅已失效（浏览器清数据/过期），顺带从库中清理。
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    /** 推送服务的离线保留时长（秒）：设备离线 1 小时内回来仍能收到 */
    private static final int TTL_SECONDS = 3600;

    private final PushSubscriptionMapper mapper;
    private final PushVapidProperties vapid;
    private final ObjectMapper objectMapper;
    /** web-push 发送器；VAPID 未配置时为 null，代表推送功能关闭。
     *  库类与业务类同名，用全限定名引用 */
    private final nl.martijndwars.webpush.PushService sender;

    public PushService(PushSubscriptionMapper mapper, PushVapidProperties vapid, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.vapid = vapid;
        this.objectMapper = objectMapper;
        nl.martijndwars.webpush.PushService pusher = null;
        if (vapid.isConfigured()) {
            try {
                pusher = new nl.martijndwars.webpush.PushService(vapid.publicKey(), vapid.privateKey(), vapid.subject());
            } catch (GeneralSecurityException e) {
                // 密钥格式非法：fail-fast 拒绝启动，避免带着坏配置静默关闭推送
                throw new IllegalStateException("blog.push.vapid 密钥格式非法，无法初始化 Web Push", e);
            }
        }
        this.sender = pusher;
        log.info("web push {}", sender != null ? "enabled, subject=" + vapid.subject() : "disabled (blog.push.vapid 未配置)");
    }

    /** 下发给前端的 VAPID 公钥（pushManager.subscribe 的 applicationServerKey） */
    public String publicKey() {
        requireConfigured();
        return vapid.publicKey();
    }

    /** 保存订阅：按 endpoint 幂等，浏览器重新生成密钥时以新值覆盖 */
    public void subscribe(String endpoint, String p256dh, String auth, Long userId) {
        var sub = new PushSubscription();
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        sub.setUserId(userId);
        mapper.upsert(sub);
        log.info("push subscription saved: endpointTail={}, userId={}", tail(endpoint), userId);
    }

    public void unsubscribe(String endpoint) {
        mapper.deleteByEndpoint(endpoint);
        log.info("push subscription removed: endpointTail={}", tail(endpoint));
    }

    /** 全量订阅（管理端列表用） */
    public List<PushSubscription> listSubscriptions() {
        return mapper.findAll();
    }

    /**
     * 向全部订阅广播一条推送。业务事件（新评论、系统消息）驱动定向发送时，
     * 复用 {@link #sendTo(PushSubscription, String)} 即可。
     *
     * @return total/sent/failed 统计
     */
    public PushSendResult sendAll(String title, String body, String url) {
        requireConfigured();
        List<PushSubscription> subs = mapper.findAll();
        if (subs.isEmpty())
            return new PushSendResult(0, 0, 0);

        String payload = payload(title, body, url);
        int sent = 0;
        int failed = 0;
        for (PushSubscription sub : subs) {
            if (sendTo(sub, payload))
                sent++;
            else
                failed++;
        }
        log.info("push broadcast: total={}, sent={}, failed={}", subs.size(), sent, failed);
        return new PushSendResult(subs.size(), sent, failed);
    }

    /**
     * 向单条订阅发送。成功记回执；404/410 视为订阅失效并从库中删除；其余失败仅计数，保留订阅下次再试。
     */
    public boolean sendTo(PushSubscription sub, String payload) {
        try {
            Notification notification = Notification.builder()
                    .endpoint(sub.getEndpoint())
                    .userPublicKey(sub.getP256dh())
                    .userAuth(sub.getAuth())
                    .payload(payload)
                    .ttl(TTL_SECONDS)
                    .build();
            int status = sender.send(notification, Encoding.AES128GCM).getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                mapper.markSent(sub.getId(), LocalDateTime.now());
                return true;
            }
            if (status == 404 || status == 410) {
                log.info("push subscription expired ({}): endpointTail={}", status, tail(sub.getEndpoint()));
                mapper.deleteByEndpoint(sub.getEndpoint());
                return false;
            }
            log.warn("push send unexpected status {}: endpointTail={}", status, tail(sub.getEndpoint()));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("push send interrupted: endpointTail={}", tail(sub.getEndpoint()));
            return false;
        } catch (Exception e) {
            log.warn("push send failed: endpointTail={}, error={}", tail(sub.getEndpoint()), e.getMessage());
            return false;
        }
    }

    /** SW 送达回执：收到 push 事件即更新订阅行的回执时间 */
    public void markDelivered(String endpoint) {
        mapper.markDelivered(endpoint, LocalDateTime.now());
    }

    /** 送达概况：已确认送达的订阅数与最近一次回执时间（页面轮询展示用） */
    public DeliveredResponse deliveredSummary() {
        return new DeliveredResponse(mapper.countDelivered(), mapper.maxDeliveredAt());
    }

    private void requireConfigured() {
        if (sender == null)
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE,
                    "推送功能未配置：请在 local/application-local.yml 或环境变量设置 blog.push.vapid.*");
    }

    /** 推送载荷：{"title","body","url"}，与前端 sw.ts 的 push 事件解析约定一致 */
    private String payload(String title, String body, String url) {
        try {
            return objectMapper.writeValueAsString(new PushPayload(title, body, url));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("push payload 序列化失败", e);
        }
    }

    private static String tail(String endpoint) {
        return endpoint == null || endpoint.length() <= 24 ? endpoint : endpoint.substring(endpoint.length() - 24);
    }

    /** 推送载荷（仅内部序列化用） */
    record PushPayload(String title, String body, String url) {
    }
}
