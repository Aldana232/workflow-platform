package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "task_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmission {
    @Id
    private String id;
    private String tramiteId;
    private String processId;
    private String nodeId;
    private String userId;
    private String departmentId;
    private Map<String, Object> formData;
    private String comments;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private Integer durationMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
