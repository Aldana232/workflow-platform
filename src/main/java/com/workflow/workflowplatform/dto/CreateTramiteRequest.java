package com.workflow.workflowplatform.dto;

import com.workflow.workflowplatform.model.ClienteInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTramiteRequest {
    private String processId;
    private ClienteInfo clienteInfo;
}
