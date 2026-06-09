package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEvent {

    private String eventType;
    private String userId;
    private String userName;
    private String description;
    private LocalDateTime timestamp;
    private Map<String, String> metadata;
}
