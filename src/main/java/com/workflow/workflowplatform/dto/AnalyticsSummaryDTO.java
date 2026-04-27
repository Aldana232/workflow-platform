package com.workflow.workflowplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la respuesta del summary de analíticas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryDTO {
    
    @JsonProperty("totalActiveTramites")
    private Long totalActiveTramites;
    
    @JsonProperty("totalCompletedThisMonth")
    private Long totalCompletedThisMonth;
    
    @JsonProperty("avgDurationMinutes")
    private Double avgDurationMinutes;
    
    @JsonProperty("totalActiveProcesses")
    private Long totalActiveProcesses;
}
