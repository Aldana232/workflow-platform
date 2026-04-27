package com.workflow.workflowplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para los trámites activos de una compañía
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveTramiteDTO {
    
    @JsonProperty("code")
    private String code;
    
    @JsonProperty("processName")
    private String processName;
    
    @JsonProperty("currentNode")
    private String currentNode;
    
    @JsonProperty("departmentName")
    private String departmentName;
    
    @JsonProperty("waitingMinutes")
    private Long waitingMinutes;
}
