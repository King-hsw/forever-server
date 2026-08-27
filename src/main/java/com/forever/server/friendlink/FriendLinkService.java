package com.forever.server.friendlink;

import com.forever.server.common.BizException;
import com.forever.server.common.ErrorCode;
import com.forever.server.common.Strings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class FriendLinkService {

    private final FriendLinkMapper mapper;

    public FriendLinkService(FriendLinkMapper mapper) {
        this.mapper = mapper;
    }

    // ---------- 公开端 ----------

    /** 前台展示：仅审核通过的友链 */
    public List<FriendLinkResponse> listApproved() {
        return mapper.findApproved().stream().map(FriendLinkResponse::publicView).toList();
    }

    /** 访客提交友链申请 */
    @Transactional
    public FriendLinkResponse apply(FriendLinkApplyRequest request) {
        String siteUrl = Strings.checkHttpUrl(request.siteUrl(), "站点地址");
        if (request.iconUrl() != null && !request.iconUrl().isBlank()) {
            Strings.checkHttpUrl(request.iconUrl(), "图标地址");
        }
        String normalized = normalize(siteUrl);
        if (mapper.countBySiteUrl(normalized) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该站点已存在或已在审核中，请勿重复申请");
        }

        FriendLink link = new FriendLink();
        link.setName(request.name().trim());
        link.setSiteUrl(normalized);
        link.setIconUrl(Strings.blankToNull(request.iconUrl()));
        link.setDescription(Strings.blankToNull(request.description()));
        link.setContact(Strings.blankToNull(request.contact()));
        link.setStatus(FriendLinkStatus.PENDING);
        mapper.insert(link);
        log.info("friend link applied: id={}, name={}, siteUrl={}", link.getId(), link.getName(), link.getSiteUrl());
        return FriendLinkResponse.publicView(mapper.findById(link.getId()));
    }

    // ---------- 管理端 ----------

    /** 全量列表（含待审核与已驳回） */
    public List<FriendLinkResponse> listAll() {
        return mapper.findAll().stream().map(FriendLinkResponse::adminView).toList();
    }

    /** 管理端主动创建友链：无需审核，创建即通过 */
    @Transactional
    public FriendLinkResponse create(FriendLinkApplyRequest request) {
        String siteUrl = Strings.checkHttpUrl(request.siteUrl(), "站点地址");
        if (request.iconUrl() != null && !request.iconUrl().isBlank()) {
            Strings.checkHttpUrl(request.iconUrl(), "图标地址");
        }
        String normalized = normalize(siteUrl);
        if (mapper.countBySiteUrl(normalized) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该站点地址已存在");
        }

        FriendLink link = new FriendLink();
        link.setName(request.name().trim());
        link.setSiteUrl(normalized);
        link.setIconUrl(Strings.blankToNull(request.iconUrl()));
        link.setDescription(Strings.blankToNull(request.description()));
        link.setContact(Strings.blankToNull(request.contact()));
        link.setStatus(FriendLinkStatus.APPROVED);
        link.setReviewedAt(LocalDateTime.now());
        mapper.insert(link);
        log.info("friend link created by admin: id={}, name={}, siteUrl={}", link.getId(), link.getName(), link.getSiteUrl());
        return FriendLinkResponse.adminView(mapper.findById(link.getId()));
    }

    @Transactional
    public FriendLinkResponse update(Long id, FriendLinkUpdateRequest request) {
        FriendLink exists = requireExists(id);
        String siteUrl = normalize(Strings.checkHttpUrl(request.siteUrl(), "站点地址"));
        long dup = mapper.countBySiteUrl(siteUrl);
        // 除自身外不允许重复的站点地址
        boolean duplicated = dup > 1 || (dup == 1 && !normalize(exists.getSiteUrl()).equals(siteUrl));
        if (duplicated) {
            throw new BizException(ErrorCode.CONFLICT, "该站点地址已存在");
        }

        FriendLink link = exists;
        link.setName(request.name().trim());
        link.setSiteUrl(siteUrl);
        link.setIconUrl(Strings.blankToNull(request.iconUrl()));
        link.setDescription(Strings.blankToNull(request.description()));
        link.setStatus(request.status());
        link.setRejectReason(request.status() == FriendLinkStatus.REJECTED ? Strings.blankToNull(request.rejectReason()) : null);
        mapper.update(link);
        log.info("friend link updated: id={}, status={}", id, link.getStatus());
        return FriendLinkResponse.adminView(link);
    }

    @Transactional
    public FriendLinkResponse approve(Long id) {
        requireExists(id);
        mapper.approve(id, LocalDateTime.now());
        log.info("friend link approved: id={}", id);
        return FriendLinkResponse.adminView(mapper.findById(id));
    }

    @Transactional
    public FriendLinkResponse reject(Long id, String reason) {
        requireExists(id);
        mapper.reject(id, Strings.blankToNull(reason), LocalDateTime.now());
        log.info("friend link rejected: id={}", id);
        return FriendLinkResponse.adminView(mapper.findById(id));
    }

    @Transactional
    public void delete(Long id) {
        requireExists(id);
        mapper.deleteById(id);
        log.info("friend link deleted: id={}", id);
    }

    // ---------- internal ----------

    FriendLink requireExists(Long id) {
        FriendLink link = mapper.findById(id);
        if (link == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "友链不存在");
        }
        return link;
    }

    /** 去掉末尾斜杠，避免同一站点因结尾差异重复申请 */
    private static String normalize(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") && trimmed.length() > 1
                ? trimmed.substring(0, trimmed.length() - 1)
                : trimmed;
    }
}
