package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "fcm_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmToken {

    @Id
    private String id;

    private String tramiteCode;

    private String fcmToken;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
