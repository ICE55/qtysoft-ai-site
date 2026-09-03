package com.qtysoft.cms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qtysoft.cms.dto.ContentSummary;
import com.qtysoft.cms.dto.RevisionView;
import com.qtysoft.cms.model.Document;
import com.qtysoft.cms.model.DocumentStatus;
import com.qtysoft.cms.model.Revision;
import com.qtysoft.cms.model.User;
import com.qtysoft.cms.repository.DocumentRepository;
import com.qtysoft.cms.repository.RevisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private static final List<String> DOC_KEYS =
            List.of("site", "home", "product", "solutions", "cases", "about");

    private static final Map<String, String> DOC_LABELS = Map.of(
            "site", "站点设置",
            "home", "首页",
            "product", "产品能力",
            "solutions", "行业方案",
            "cases", "客户案例",
            "about", "关于我们"
    );

    private final DocumentRepository documentRepository;
    private final RevisionRepository revisionRepository;
    private final ContentSchema contentSchema;
    private final DeployService deployService;
    private final ObjectMapper objectMapper;

    private final Map<String, String> seedCache = new HashMap<>();

    @PostConstruct
    public void loadSeed() {
        for (String key : DOC_KEYS) {
            try {
                ClassPathResource res = new ClassPathResource("seed/" + key + ".json");
                seedCache.put(key, new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.error("加载种子内容失败: {}", key, e);
                seedCache.put(key, "{}");
            }
        }
    }

    public Map<String, Object> getSchema(String docKey) {
        return contentSchema.getSchema(docKey);
    }

    /** 仪表盘汇总：各文档状态与最近更新 */
    @Transactional(readOnly = true)
    public List<com.qtysoft.cms.dto.ContentSummary> summary() {
        return DOC_KEYS.stream().map(key -> {
            Document doc = documentRepository.findByDocKey(key).orElse(null);
            return com.qtysoft.cms.dto.ContentSummary.builder()
                    .key(key)
                    .label(DOC_LABELS.getOrDefault(key, key))
                    .status(doc == null ? DocumentStatus.DRAFT : doc.getStatus())
                    .updatedAt(doc == null ? null : doc.getUpdatedAt())
                    .updatedBy(doc == null || doc.getUpdatedBy() == null ? "" : String.valueOf(doc.getUpdatedBy()))
                    .build();
        }).collect(Collectors.toList());
    }

    /** 取草稿；若库内暂无该文档，返回种子默认内容（便于首次编辑） */
    @Transactional(readOnly = true)
    public Object getDraft(String docKey) {
        ensureKey(docKey);
        return documentRepository.findByDocKey(docKey)
                .map(d -> parse(d.getDataJson()))
                .orElseGet(() -> parse(seedCache.get(docKey)));
    }

    /** 保存草稿：更新数据，并标记为「未发布改动」(DRAFT) */
    @Transactional
    public Object saveDraft(String docKey, Object data, User currentUser) {
        ensureKey(docKey);
        String json = serialize(data);
        Document doc = documentRepository.findByDocKey(docKey).orElseGet(() ->
                Document.builder().docKey(docKey).status(DocumentStatus.DRAFT).build());
        doc.setDataJson(json);
        doc.setStatus(DocumentStatus.DRAFT);
        doc.setUpdatedAt(java.time.Instant.now());
        doc.setUpdatedBy(currentUser.getId());
        documentRepository.save(doc);
        return parse(json);
    }

    /** 发布：标记为已发布 + 写版本快照 + 触发重建 */
    @Transactional
    public RevisionView publish(String docKey, User currentUser, String note) {
        ensureKey(docKey);
        Document doc = documentRepository.findByDocKey(docKey)
                .orElseGet(() -> {
                    Document d = Document.builder().docKey(docKey).build();
                    d.setDataJson(seedCache.get(docKey));
                    return d;
                });
        if (doc.getStatus() != DocumentStatus.PUBLISHED) {
            doc.setStatus(DocumentStatus.PUBLISHED);
        }
        doc.setUpdatedAt(java.time.Instant.now());
        doc.setUpdatedBy(currentUser.getId());
        documentRepository.save(doc);

        Revision rev = Revision.builder()
                .docId(doc.getId())
                .docKey(docKey)
                .dataJson(doc.getDataJson())
                .note(note == null || note.isBlank() ? "发布" : note)
                .createdById(currentUser.getId())
                .createdByName(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getUsername())
                .build();
        revisionRepository.save(rev);

        deployService.trigger();
        return toView(rev);
    }

    @Transactional(readOnly = true)
    public List<RevisionView> history(String docKey) {
        ensureKey(docKey);
        return revisionRepository.findByDocKeyOrderByCreatedAtDesc(docKey).stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /** 回滚：把某历史快照设为草稿并重新发布 */
    @Transactional
    public RevisionView restore(String docKey, Long revId, User currentUser) {
        ensureKey(docKey);
        Revision rev = revisionRepository.findById(revId)
                .orElseThrow(() -> new IllegalArgumentException("历史版本不存在"));
        if (!rev.getDocKey().equals(docKey)) {
            throw new IllegalArgumentException("历史版本与文档不匹配");
        }
        Document doc = documentRepository.findByDocKey(docKey)
                .orElseGet(() -> Document.builder().docKey(docKey).build());
        doc.setDataJson(rev.getDataJson());
        doc.setStatus(DocumentStatus.PUBLISHED);
        doc.setUpdatedAt(java.time.Instant.now());
        doc.setUpdatedBy(currentUser.getId());
        documentRepository.save(doc);

        Revision newRev = Revision.builder()
                .docId(doc.getId())
                .docKey(docKey)
                .dataJson(rev.getDataJson())
                .note("回滚至 v" + revId)
                .createdById(currentUser.getId())
                .createdByName(currentUser.getDisplayName() != null ? currentUser.getDisplayName() : currentUser.getUsername())
                .build();
        revisionRepository.save(newRev);
        deployService.trigger();
        return toView(newRev);
    }

    /** 已发布内容（供构建拉取 / 部署令牌鉴权在 Controller 层） */
    @Transactional(readOnly = true)
    public Map<String, Object> getPublished() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : DOC_KEYS) {
            documentRepository.findByDocKey(key).ifPresent(d -> {
                if (d.getStatus() == DocumentStatus.PUBLISHED) {
                    result.put(key, parse(d.getDataJson()));
                }
            });
        }
        return result;
    }

    /** 首次启动且文档表为空时，写入种子并标记为已发布 */
    @Transactional
    public void seedDefaultDocumentsIfEmpty() {
        if (documentRepository.count() > 0) return;
        for (String key : DOC_KEYS) {
            Document doc = Document.builder()
                    .docKey(key)
                    .dataJson(seedCache.get(key))
                    .status(DocumentStatus.PUBLISHED)
                    .build();
            documentRepository.save(doc);
            log.info("已写入种子文档: {}", key);
        }
    }

    // ---------- helpers ----------
    private void ensureKey(String docKey) {
        if (!DOC_KEYS.contains(docKey)) {
            throw new IllegalArgumentException("未知文档: " + docKey);
        }
    }

    private Object parse(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.warn("解析内容 JSON 失败，回退为空对象: {}", e.getMessage());
            return new LinkedHashMap<String, Object>();
        }
    }

    private String serialize(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("内容序列化失败: " + e.getMessage());
        }
    }

    private RevisionView toView(Revision rev) {
        return RevisionView.builder()
                .id(rev.getId())
                .docKey(rev.getDocKey())
                .note(rev.getNote())
                .createdByName(rev.getCreatedByName())
                .createdAt(rev.getCreatedAt())
                .build();
    }
}
