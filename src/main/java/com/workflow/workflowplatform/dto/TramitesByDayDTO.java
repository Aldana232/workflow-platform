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
public class TramitesByDayDTO {

    // Fecha en formato "YYYY-MM-DD"
    @JsonProperty("date")
    private String date;

    @JsonProperty("count")
    private Long count;
}
