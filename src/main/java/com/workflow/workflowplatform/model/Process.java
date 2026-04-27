package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.workflow.workflowplatform.model.enums.ProcessStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "processes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Process {
    @Id
    private String id;
    private String name;
    private String description;
    private ProcessStatus status;
    private String companyId;
    private String createdBy;
    private List<Node> nodes;
    private List<Edge> edges;
    private String bpmnXml;
    private Map<String, Object> nodeProperties;
    private String formSchemaId;
    private Integer version;
    private Integer activeTramites;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
