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
public class BottleneckDTO {

    @JsonProperty("nodeId")
    private String nodeId;

    @JsonProperty("processId")
    private String processId;

    // Duración promedio real de las tareas en ese nodo (minutos)
    @JsonProperty("avgDuration")
    private Double avgDuration;

    // SLA definido para el nodo (convertido a minutos desde slaHours)
    @JsonProperty("sla")
    private Integer sla;

    // Exceso sobre el SLA: avgDuration - sla (minutos)
    @JsonProperty("excess")
    private Double excess;
}
