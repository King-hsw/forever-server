package com.forever.server.actionlog;

import com.forever.server.common.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志。写入原则：记录失败绝不影响业务请求——任何异常只记运行日志。
 */
@Slf4j
@Service
public class ActionLogService {

    private final ActionLogMapper actionLogMapper;

    public ActionLogService(ActionLogMapper actionLogMapper) {
        this.actionLogMapper = actionLogMapper;
    }

    /** 记录一条审计日志；失败静默，不抛出 */
    public void record(String username, String method, String path,
                       int status, String ip, Long durationMs) {
        try {
            ActionLog entry = new ActionLog();
            entry.setUsername(username);
            entry.setMethod(method);
            entry.setPath(path);
            entry.setStatus(status);
            entry.setIp(ip);
            entry.setDurationMs(durationMs);
            actionLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("action log insert failed: path={}, reason={}", path, e.getMessage());
        }
    }

    public PageResult<ActionLog> page(int page, int size, String username, String path) {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 100);
        int offset = (page - 1) * size;
        return PageResult.of(
                actionLogMapper.page(username, path, offset, size),
                actionLogMapper.count(username, path), page, size);
    }
}
