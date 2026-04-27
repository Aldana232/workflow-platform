package com.workflow.workflowplatform.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.workflow.workflowplatform.model.enums.NodeType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    @JsonAlias("nodeId")
    private String id;
    
    private NodeType type;
    
    @JsonAlias("name")
    private String label;
    
    private Integer x;
    private Integer y;
    private String assignedUser;
    private String departmentId;
    private Integer slaHours;
}
