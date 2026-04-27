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
public class NodeCountDTO {

    @JsonProperty("nodeId")
    private String nodeId;

    // Cantidad de trámites activos actualmente en este nodo
    @JsonProperty("count")
    private Long count;
}
