package com.workflow.workflowplatform.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DocumentEventRequest {

    private String documentId;
    private String eventType;
    private String description;
}
