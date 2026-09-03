package com.qtysoft.cms.dto;

import com.qtysoft.cms.model.DocumentStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContentSummary {
    private String key;
    private String label;
    private DocumentStatus status;
    private Instant updatedAt;
    private String updatedBy;
}
