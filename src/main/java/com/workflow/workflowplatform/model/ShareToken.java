package com.workflow.workflowplatform.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "share_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareToken {

    @Id
    private String id;
    private String token;
    private String tramiteId;
    private String tramiteCode;
    private String createdBy;
    private String createdByName;
    private LocalDateTime expiresAt;
    @Builder.Default private boolean active = true;
    @Builder.Default private int accessCount = 0;
    private LocalDateTime createdAt;
}
