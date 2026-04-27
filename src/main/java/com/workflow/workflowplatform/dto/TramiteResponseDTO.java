package com.workflow.workflowplatform.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TramiteResponseDTO {
    private String tramiteId;
    private String code;
    private String currentNodeId;
    private String currentNodeLabel;
    private String clienteNombre;
    private LocalDateTime startedAt;
    private String status;
}
