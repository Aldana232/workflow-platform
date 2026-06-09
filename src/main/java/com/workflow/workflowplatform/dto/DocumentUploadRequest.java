package com.workflow.workflowplatform.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class DocumentUploadRequest {

    private String tramiteId;
    private String tramiteCode;
    private String companyId;
    private String nodeId;
    private String nodeName;
    private String category;
    private String description;
    private List<String> tags;
}
