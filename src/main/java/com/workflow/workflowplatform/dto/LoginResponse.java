package com.workflow.workflowplatform.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.workflow.workflowplatform.model.enums.UserRole;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private UserRole role;
    private String name;
    private String userId;
}
