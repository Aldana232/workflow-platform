package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import com.workflow.workflowplatform.model.enums.TramiteStatus;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "tramites")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tramite {
    @Id
    private String id;
    private String code;
    private String processId;
    private String userId;
    private ClienteInfo clienteInfo;
    private String createdBy;
    private String currentNodeId;
    private List<String> parallelPending;
    private TramiteStatus status;
    private List<HistoryEntry> history;
    private String companyId;
    private String departmentId;
    private LocalDateTime startDate;
    private LocalDateTime completedDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
