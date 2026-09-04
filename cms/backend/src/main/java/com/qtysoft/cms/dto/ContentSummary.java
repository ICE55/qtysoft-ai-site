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
    /** 存在已保存但尚未发布的改动 */
    private boolean hasUnpublishedChanges;
    private Instant updatedAt;
    private String updatedBy;
}
