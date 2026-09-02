package com.forever.server.sensitive;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 敏感词库。全量缓存在内存中（个人博客词量小），
 * 增删改时刷新缓存；评论提交时按缓存做替换过滤。
 */
@Slf4j
@Service
public class SensitiveWordService {

    /**
     * 单条缓存快照：不可变列表，读多写少场景无锁安全
     */
    private final AtomicReference<List<SensitiveWord>> cache =
            new AtomicReference<>(List.of());

    private final SensitiveWordMapper mapper;

    public SensitiveWordService(SensitiveWordMapper mapper) {
        this.mapper = mapper;
        refreshCache();
    }

    public List<SensitiveWordResponse> listAll() {
        return cache.get().stream()
                .map(w -> new SensitiveWordResponse(w.getId(), w.getWord(), w.getReplacement(), w.getCreatedAt()))
                .toList();
    }

    public SensitiveWordResponse create(SensitiveWordRequest request) {
        checkUnique(request.word(), null);
        SensitiveWord word = new SensitiveWord();
        word.setWord(request.word());
        word.setReplacement(request.replacement() == null || request.replacement().isBlank()
                ? "***" : request.replacement());
        mapper.insert(word);
        refreshCache();
        log.info("sensitive word created: id={}, word={}", word.getId(), word.getWord());
        return toResponse(word);
    }

    public SensitiveWordResponse update(Long id, SensitiveWordRequest request) {
        SensitiveWord exists = requireExists(id);
        checkUnique(request.word(), id);
        exists.setWord(request.word());
        exists.setReplacement(request.replacement() == null || request.replacement().isBlank()
                ? "***" : request.replacement());
        mapper.update(exists);
        refreshCache();
        log.info("sensitive word updated: id={}, word={}", id, exists.getWord());
        return toResponse(exists);
    }

    public void delete(Long id) {
        requireExists(id);
        mapper.deleteById(id);
        refreshCache();
        log.info("sensitive word deleted: id={}", id);
    }

    /**
     * 评论内容打码：命中词替换为对应 replacement
     */
    public String mask(String content) {
        String result = content;
        for (SensitiveWord w : cache.get()) {
            result = result.replace(w.getWord(), w.getReplacement());
        }
        return result;
    }

    // ---------- internal ----------

    private void refreshCache() {
        cache.set(List.copyOf(mapper.findAll()));
    }

    private SensitiveWord requireExists(Long id) {
        SensitiveWord word = cache.get().stream()
                .filter(w -> w.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (word == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "敏感词不存在");
        }
        return word;
    }

    private void checkUnique(String word, Long excludeId) {
        boolean duplicated = cache.get().stream()
                .anyMatch(w -> w.getWord().equals(word) && !w.getId().equals(excludeId));
        if (duplicated) {
            throw new BizException(ErrorCode.CONFLICT, "敏感词已存在");
        }
    }

    private SensitiveWordResponse toResponse(SensitiveWord w) {
        return new SensitiveWordResponse(w.getId(), w.getWord(), w.getReplacement(), w.getCreatedAt());
    }
}
