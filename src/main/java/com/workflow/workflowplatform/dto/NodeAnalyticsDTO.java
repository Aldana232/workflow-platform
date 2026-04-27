package com.workflow.workflowplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para el análisis de cada nodo dentro de un proceso
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeAnalyticsDTO {
    
    @JsonProperty("nodeId")
    private String nodeId;
    
    @JsonProperty("nodeName")
    private String nodeName;
    
    @JsonProperty("avgDurationMinutes")
    private Double avgDurationMinutes;
    
    @JsonProperty("totalTasks")
    private Long totalTasks;
}
