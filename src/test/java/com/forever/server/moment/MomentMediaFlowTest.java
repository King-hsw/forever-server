package com.forever.server.moment;

import com.forever.server.auth.RbacService;
import com.forever.server.auth.SysUserMapper;
import com.forever.server.comment.CommentMapper;
import com.forever.server.common.BizException;
import com.forever.server.setting.SiteConfigService;
import com.forever.server.storage.StorageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 直传收口流程：非法 key 拒绝、对象缺失拒绝、成功收口为正式对象 */
class MomentMediaFlowTest {

    static final String TMP_KEY = "tmp/2026/08/0123456789abcdef0123456789abcdef.png";

    final MomentMapper momentMapper = mock(MomentMapper.class);
    final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    final CommentMapper commentMapper = mock(CommentMapper.class);
    final RbacService rbacService = mock(RbacService.class);
    final SiteConfigService siteConfig = mock(SiteConfigService.class);
    final StorageService storage = mock(StorageService.class);

    final MomentService service = new MomentService(
            momentMapper, sysUserMapper, commentMapper, rbacService, siteConfig, storage);

    private MomentCreateRequest request(List<String> images) {
        return new MomentCreateRequest("今日份摸鱼", images, null, null, null, null, null);
    }

    @Test
    void 非法tmpKey直接拒绝() {
        var e = assertThrows(BizException.class,
                () -> service.create(1, request(List.of("tmp/../../evil.jpg"))));
        assertTrue(e.getMessage().contains("非法的直传文件标识"));
        verify(storage, never()).stat(anyString());
    }

    @Test
    void 直传对象缺失时拒绝发布() {
        when(storage.stat(TMP_KEY)).thenReturn(null);
        var e = assertThrows(BizException.class, () -> service.create(1, request(List.of(TMP_KEY))));
        assertTrue(e.getMessage().contains("直传文件不存在"));
    }

    @Test
    void 成功收口为正式对象并删除暂存() {
        when(storage.stat(TMP_KEY)).thenReturn(new StorageService.Stat("image/png", 100L));
        var response = service.create(1, request(List.of(TMP_KEY)));
        assertEquals(1, response.media().images().size());
        String url = response.media().images().get(0);
        assertTrue(url.startsWith("/uploads/moment/2026/08/"));
        assertTrue(url.endsWith(".png"));
        verify(storage).copy(eq(TMP_KEY), anyString(), eq("image/png"));
        verify(storage).delete(TMP_KEY);
    }

    @Test
    void 直传类型不在白名单时拒绝() {
        when(storage.stat(TMP_KEY)).thenReturn(new StorageService.Stat("application/zip", 100L));
        assertThrows(BizException.class, () -> service.create(1, request(List.of(TMP_KEY))));
        verify(storage, never()).copy(anyString(), anyString(), anyString());
    }

    @Test
    void 直传超限拒绝() {
        when(storage.stat(TMP_KEY)).thenReturn(new StorageService.Stat("image/png", 6L * 1024 * 1024));
        var e = assertThrows(BizException.class, () -> service.create(1, request(List.of(TMP_KEY))));
        assertTrue(e.getMessage().contains("大小超出限制"));
    }

    @Test
    void presign白名单外的类型拒绝() {
        assertThrows(BizException.class, () -> service.presignUpload("application/zip"));
        assertThrows(BizException.class, () -> service.presignUpload(null));
    }
}
