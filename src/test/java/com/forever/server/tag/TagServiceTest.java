package com.forever.server.tag;

import com.forever.server.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagServiceTest {

    /** 假 Mapper：只覆盖 create 用到的三个方法，其余不实现 */
    private record FakeMapper(long count, boolean insertThrows, Tag existingByName) {
        TagMapper asMapper() {
            return new TagMapper() {
                @Override
                public int insert(Tag tag) {
                    if (insertThrows) throw new DataIntegrityViolationException("duplicate key");
                    tag.setId(99L);
                    return 1;
                }

                @Override
                public long countByName(String name) {
                    return count;
                }

                @Override
                public Tag findByIdName(String name) {
                    return existingByName;
                }

                @Override public int update(Tag tag) { throw new UnsupportedOperationException(); }
                @Override public int deleteById(Long id) { throw new UnsupportedOperationException(); }
                @Override public int deleteRelationsByTagId(Long tagId) { throw new UnsupportedOperationException(); }
                @Override public Tag findById(Long id) { throw new UnsupportedOperationException(); }
                @Override public java.util.List<TagCountRow> listWithPublishedCount() { throw new UnsupportedOperationException(); }
            };
        }
    }

    private static Tag named(long id, String name) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        return t;
    }

    @Test
    void create_concurrentDuplicateReturnsExisting() {
        // 并发竞态：check-then-act 之间另一请求已插入，唯一索引拒绝第二次 insert
        // → 幂等返回已存在行，而不是抛 500
        FakeMapper m = new FakeMapper(0, true, named(5, "Bug"));
        TagResponse r = new TagService(m.asMapper()).create(new TagRequest("Bug"));
        assertEquals(5L, r.id());
        assertEquals("Bug", r.name());
    }

    @Test
    void create_newNameInserts() {
        FakeMapper m = new FakeMapper(0, false, null);
        TagResponse r = new TagService(m.asMapper()).create(new TagRequest("新标签"));
        assertEquals(99L, r.id());
        assertEquals("新标签", r.name());
    }

    @Test
    void create_existingNameThrowsConflict() {
        FakeMapper m = new FakeMapper(1, false, null);
        BizException e = assertThrows(BizException.class,
                () -> new TagService(m.asMapper()).create(new TagRequest("Bug")));
        assertTrue(e.getMessage().contains("已存在"));
    }
}
