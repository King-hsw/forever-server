package com.forever.server.message;

import com.forever.server.auth.SysUser;
import com.forever.server.auth.SysUserMapper;
import com.forever.server.comment.Comment;
import com.forever.server.comment.CommentCreatedEvent;
import com.forever.server.common.PageResult;
import com.forever.server.common.Strings;
import com.forever.server.config.BlogProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内消息收件箱：订阅评论创建事件，按规则写入收件人；读删操作仅本人可见。
 * 收件箱是记录而非推送，不受 comment.notify-mail 开关控制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    public static final String TYPE_COMMENT_REPLY = "COMMENT_REPLY";
    public static final String TYPE_NEW_COMMENT = "NEW_COMMENT";

    private final MessageMapper messageMapper;
    private final SysUserMapper sysUserMapper;
    private final BlogProperties blogProperties;

    /**
     * 评论落库 → 写入相关收件人；失败只记日志，不影响评论
     */
    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        try {
            doCreate(event);
        } catch (Exception ex) {
            log.warn("站内消息写入失败: commentId={}, reason={}", event.comment().getId(), ex.getMessage());
        }
    }

    private void doCreate(CommentCreatedEvent event) {
        Comment comment = event.comment();
        if (event.parent() != null) {
            // 回复：被回复者是登录用户（评论上有归属账号）时写入其收件箱
            Long targetUid = event.parent().getUserId();
            if (targetUid != null && !targetUid.equals(comment.getUserId())) {
                insert(targetUid, TYPE_COMMENT_REPLY,
                        "「" + comment.getNickname() + "」在《" + event.sourceTitle() + "》下回复了你的评论：" + excerpt(comment.getContent()),
                        event.sourceUrl());
            }
            return;
        }
        // 根评论：写入站长账号收件箱；自己评自己不写
        Long ownerUid = ownerUid();
        if (ownerUid != null && !ownerUid.equals(comment.getUserId())) {
            insert(ownerUid, TYPE_NEW_COMMENT,
                    "《" + event.sourceTitle() + "》收到新评论：" + comment.getNickname() + "：" + excerpt(comment.getContent()),
                    event.sourceUrl());
        }
    }

    private void insert(Long userId, String type, String content, String sourceUrl) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setContent(content);
        message.setSourceUrl(sourceUrl);
        messageMapper.insert(message);
    }

    /**
     * 站长账号 uid（与 CommentNotifyService 的判定保持一致）
     */
    private Long ownerUid() {
        String username = blogProperties.admin().username();
        if (username == null || username.isBlank()) {
            return null;
        }
        SysUser owner = sysUserMapper.findByUsername(username);
        return owner == null ? null : owner.getId();
    }

    private static String excerpt(String content) {
        return Strings.excerpt(content, 80);
    }

    public PageResult<MessageDtos.MessageResponse> page(long uid, int page, int size) {
        long total = messageMapper.countByUser(uid);
        int offset = (page - 1) * size;
        if (offset >= total) {
            return PageResult.of(List.of(), total, page, size);
        }
        List<MessageDtos.MessageResponse> list = messageMapper.pageByUser(uid, offset, size).stream()
                .map(m -> new MessageDtos.MessageResponse(
                        m.getId(), m.getType(), m.getContent(), m.getSourceUrl(),
                        Boolean.TRUE.equals(m.getIsRead()), m.getCreatedAt()))
                .toList();
        return PageResult.of(list, total, page, size);
    }

    public long unreadCount(long uid) {
        return messageMapper.countUnread(uid);
    }

    public void markRead(long uid, long id) {
        messageMapper.markRead(id, uid);
    }

    public void markAllRead(long uid) {
        messageMapper.markAllRead(uid);
    }

    public void delete(long uid, long id) {
        messageMapper.softDelete(id, uid);
    }
}
