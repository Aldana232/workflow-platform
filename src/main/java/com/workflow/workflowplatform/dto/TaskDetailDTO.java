package com.workflow.workflowplatform.dto;

import com.workflow.workflowplatform.model.ClienteInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDetailDTO {
    private String id;             // TaskSubmission.id
    private String tramiteId;
    private String tramiteCode;
    private String processId;
    private String processName;
    private String nodeId;
    private String nodeName;
    private String departmentId;
    private Integer slaHours;
    private String status;         // tramite status
    private LocalDateTime arrivedAt;
    private ClienteInfo clienteInfo;
    private Boolean canProcess;
}
