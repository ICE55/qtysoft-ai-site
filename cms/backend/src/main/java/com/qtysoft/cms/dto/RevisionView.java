package com.qtysoft.cms.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionView {
    private Long id;
    private String docKey;
    private String note;
    private String createdByName;
    private Instant createdAt;
}
