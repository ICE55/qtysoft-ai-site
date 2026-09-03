package com.qtysoft.cms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cms_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Revision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_id")
    private Long docId;

    @Column(name = "doc_key", nullable = false, length = 40)
    private String docKey;

    @Column(name = "data_json", columnDefinition = "text", nullable = false)
    private String dataJson;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @Column(name = "created_by")
    private Long createdById;

    @Column(name = "created_by_name")
    private String createdByName;
}
