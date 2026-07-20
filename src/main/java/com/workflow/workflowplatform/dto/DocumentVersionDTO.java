package com.workflow.workflowplatform.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DocumentVersionDTO {
    private String versionId;
    private LocalDateTime lastModified;
    private long sizeBytes;
    private boolean isLatest;
}
