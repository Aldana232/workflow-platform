package com.workflow.workflowplatform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AssistantRequest {

    @NotBlank(message = "La pregunta es obligatoria")
    @Size(max = 500, message = "La pregunta no puede superar los 500 caracteres")
    private String question;

    @NotBlank(message = "El tramiteId es obligatorio")
    private String tramiteId;

    /** Opcional. Si es null se usa tramite.currentNodeId */
    private String nodeId;
}
