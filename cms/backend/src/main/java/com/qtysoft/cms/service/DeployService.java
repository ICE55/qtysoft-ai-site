package com.qtysoft.cms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 触发公开站重建。支持两类钩子：
 *  - Vercel Deploy Hook：直接 POST 该 URL 即可。
 *  - GitHub Actions workflow_dispatch：POST 到
 *    https://api.github.com/repos/<owner>/<repo>/actions/workflows/<file>/dispatches
 *    并在 Header 带 Authorization: Bearer <token>，body 为 {"ref":"main"}。
 * 通过 DEPLOY_HOOK_URL 与 CMS_DEPLOY_TOKEN 配置。
 */
@Service
@Slf4j
public class DeployService {

    @Value("${cms.deploy.hook-url:}")
    private String hookUrl;

    @Value("${cms.deploy.token:}")
    private String token;

    private final RestTemplate rest = new RestTemplate();

    public boolean trigger() {
        if (hookUrl == null || hookUrl.isBlank()) {
            log.warn("未配置 DEPLOY_HOOK_URL，跳过触发重建（内容已在数据库标记为已发布）");
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token);
            }
            // 兼容 workflow_dispatch 需要 ref；Vercel 钩子忽略 body
            String body = "{\"ref\":\"main\",\"inputs\":{}}";
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> resp = rest.postForEntity(hookUrl, entity, String.class);
            boolean ok = resp.getStatusCode().is2xxSuccessful();
            log.info("触发静态站重建：{} -> {}", hookUrl, resp.getStatusCode());
            return ok;
        } catch (Exception e) {
            log.error("触发静态站重建失败：{}", e.getMessage());
            return false;
        }
    }
}
