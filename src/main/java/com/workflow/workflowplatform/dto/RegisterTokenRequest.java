package com.workflow.workflowplatform.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RegisterTokenRequest {

    @NotBlank
    private String tramiteCode;

    @NotBlank
    private String fcmToken;
}
