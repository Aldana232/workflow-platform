package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {

    @Id
    private String id;

    private String tramiteId;
    private String tramiteCode;
    private String companyId;
    private String nodeId;
    private String nodeName;
    private String uploadedBy;
    private String uploadedByName;
    private String departmentId;
    private String departmentName;
    private String fileName;
    private String storedFileName;
    private String fileUrl;
    private String googleDocId;
    private Long fileSize;
    private String googleDocUrl;
    private String mimeType;
    private String category;
    private String description;
    @Builder.Default
    private List<DocumentEvent> events = new ArrayList<>();
    private List<String> tags;

    @Builder.Default
    private Integer version = 1;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
