package com.workflow.workflowplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentPerformanceDTO {

    @JsonProperty("department")
    private String department;

    @JsonProperty("totalTasks")
    private Long totalTasks;

    // Tiempo promedio de las tareas completadas (minutos)
    @JsonProperty("avgTime")
    private Double avgTime;

    // Porcentaje de tareas que cumplieron el SLA del nodo (0-100)
    @JsonProperty("slaCompliance")
    private Double slaCompliance;
}
