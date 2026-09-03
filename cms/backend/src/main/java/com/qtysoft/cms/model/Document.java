package com.qtysoft.cms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cms_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** site / home / product / solutions / cases / about */
    @Column(name = "doc_key", unique = true, nullable = false, length = 40)
    private String docKey;

    /** 草稿内容，JSON 文本（postgres text 列，整文档读写，不在 SQL 层查询内部） */
    @Column(name = "data_json", columnDefinition = "text", nullable = false)
    private String dataJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.DRAFT;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    void prePersist() {
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isPublished() {
        return status == DocumentStatus.PUBLISHED;
    }
}
