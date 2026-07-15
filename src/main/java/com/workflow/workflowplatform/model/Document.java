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
    private Long fileSize;
    private String mimeType;
    private String category;
    private String description;
    private List<DocumentEvent> events;
    private List<String> tags;

    @Builder.Default
    private Integer version = 1;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Campos colaborativos OnlyOffice
    @Builder.Default
    private boolean collaborativeMode = false;
    private String onlyOfficeKey;
    private String versionKey;
    @Builder.Default
    private List<String> editorIds = new ArrayList<>();  // userIds con permiso de edición
    @Builder.Default
    private List<String> viewerIds = new ArrayList<>();  // userIds con permiso de solo lectura
}
