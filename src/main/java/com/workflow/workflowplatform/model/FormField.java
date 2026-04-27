package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.workflow.workflowplatform.model.enums.FormFieldType;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormField {
    private String id;
    private String name;        // alias de id, para compatibilidad con el frontend
    private String label;
    private String placeholder;
    private FormFieldType type;
    private Boolean required;
    private List<String> options;
    private List<String> accept;
    private Integer maxMb;
}
