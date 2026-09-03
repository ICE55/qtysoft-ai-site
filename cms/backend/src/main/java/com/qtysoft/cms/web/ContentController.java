package com.qtysoft.cms.web;

import com.qtysoft.cms.dto.RevisionView;
import com.qtysoft.cms.security.CurrentUser;
import com.qtysoft.cms.model.User;
import com.qtysoft.cms.service.ContentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;
    private final CurrentUser currentUser;

    @Value("${cms.deploy.token:}")
    private String deployToken;

    @GetMapping("/schema")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> schema(@RequestParam String key) {
        return contentService.getSchema(key);
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public List<com.qtysoft.cms.dto.ContentSummary> summary() {
        return contentService.summary();
    }

    @GetMapping("/{key}")
    @PreAuthorize("isAuthenticated()")
    public Object draft(@PathVariable String key) {
        return contentService.getDraft(key);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('EDITOR','SUPER_ADMIN')")
    public Object save(@PathVariable String key, @RequestBody Object data) {
        User user = currentUser.get();
        return contentService.saveDraft(key, data, user);
    }

    @PostMapping("/{key}/publish")
    @PreAuthorize("hasAnyRole('EDITOR','SUPER_ADMIN')")
    public RevisionView publish(@PathVariable String key,
                                @RequestParam(required = false) String note) {
        User user = currentUser.get();
        return contentService.publish(key, user, note);
    }

    @GetMapping("/{key}/history")
    @PreAuthorize("isAuthenticated()")
    public List<RevisionView> history(@PathVariable String key) {
        return contentService.history(key);
    }

    @PostMapping("/{key}/restore/{revId}")
    @PreAuthorize("hasAnyRole('EDITOR','SUPER_ADMIN')")
    public RevisionView restore(@PathVariable String key, @PathVariable Long revId) {
        User user = currentUser.get();
        return contentService.restore(key, revId, user);
    }

    /** 构建拉取入口：仅接受部署令牌（与登录 JWT 隔离） */
    @GetMapping("/published")
    public Map<String, Object> published(HttpServletRequest request) {
        String provided = request.getHeader("X-Deploy-Token");
        if (deployToken == null || deployToken.isBlank()) {
            // 未配置令牌时，仅在非生产环境允许免令牌（便于本地调试）
            return contentService.getPublished();
        }
        if (provided == null || !provided.equals(deployToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "无效的部署令牌");
        }
        return contentService.getPublished();
    }
}
