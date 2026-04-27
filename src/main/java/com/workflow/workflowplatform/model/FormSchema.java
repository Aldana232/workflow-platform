package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "form_schemas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSchema {
    @Id
    private String id;
    private String processId;
    private String nodeId;
    private List<FormField> fields;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
