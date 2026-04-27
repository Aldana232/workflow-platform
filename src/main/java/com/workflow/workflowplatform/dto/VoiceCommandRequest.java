package com.workflow.workflowplatform.dto;

import lombok.Data;

@Data
public class VoiceCommandRequest {
    private String text;       // texto transcrito por Web Speech API
    private String processId;  // proceso activo en el diseñador (puede ser null)
}
