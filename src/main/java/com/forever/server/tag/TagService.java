package com.forever.server.tag;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TagService {

    private final TagMapper tagMapper;

    public TagService(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    public List<TagResponse> listAll() {
        return tagMapper.listWithPublishedCount().stream()
                .map(r -> new TagResponse(r.id(), r.name(),
                        r.articleCount() == null ? 0 : r.articleCount()))
                .toList();
    }

    public TagResponse create(TagRequest request) {
        checkNameUnique(request.name(), null);
        Tag tag = new Tag();
        tag.setName(request.name());
        tagMapper.insert(tag);
        log.info("tag created: id={}, name={}", tag.getId(), tag.getName());
        return new TagResponse(tag.getId(), tag.getName(), 0);
    }

    public TagResponse update(Long id, TagRequest request) {
        Tag tag = requireExists(id);
        checkNameUnique(request.name(), id);
        tag.setName(request.name());
        tagMapper.update(tag);
        log.info("tag updated: id={}, name={}", id, tag.getName());
        return new TagResponse(tag.getId(), tag.getName(), 0);
    }

    public void delete(Long id) {
        requireExists(id);
        tagMapper.deleteRelationsByTagId(id); // 先清关联再删标签
        tagMapper.deleteById(id);
        log.info("tag deleted: id={}, relations cleaned", id);
    }

    // ---------- internal ----------

    Tag requireExists(Long id) {
        Tag tag = tagMapper.findById(id);
        if (tag == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    private void checkNameUnique(String name, Long excludeId) {
        long count = tagMapper.countByName(name);
        if (excludeId == null ? count > 0 : count > 1) {
            throw new BizException(ErrorCode.CONFLICT, "标签名称已存在");
        }
    }
}
