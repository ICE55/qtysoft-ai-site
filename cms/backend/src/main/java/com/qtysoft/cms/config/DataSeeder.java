package com.qtysoft.cms.config;

import com.qtysoft.cms.service.AuthService;
import com.qtysoft.cms.service.ContentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final AuthService authService;
    private final ContentService contentService;

    @Value("${cms.admin.user:admin}")
    private String adminUser;

    @Value("${cms.admin.pass:ChangeMe!2026}")
    private String adminPass;

    @Value("${cms.admin.pass-force-change:true}")
    private boolean forceChange;

    @Override
    public void run(String... args) {
        try {
            authService.seedAdminIfEmpty(adminUser, adminPass, forceChange);
            contentService.seedDefaultDocumentsIfEmpty();
            log.info("CMS 初始化完成：种子管理员与默认文档已就绪。");
        } catch (Exception e) {
            log.error("CMS 初始化失败：{}", e.getMessage(), e);
        }
    }
}
