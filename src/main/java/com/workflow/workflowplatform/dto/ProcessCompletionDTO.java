package com.workflow.workflowplatform.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessCompletionDTO {

    @JsonProperty("processId")
    private String processId;

    // Distribución de trámites activos por nodo dentro del proceso
    @JsonProperty("nodes")
    private List<NodeCountDTO> nodes;
}
