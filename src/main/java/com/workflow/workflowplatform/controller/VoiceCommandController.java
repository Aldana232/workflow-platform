package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.VoiceCommand;
import com.workflow.workflowplatform.dto.VoiceCommandRequest;
import com.workflow.workflowplatform.service.VoiceCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceCommandController {

    private final VoiceCommandService voiceCommandService;

    /**
     * Recibe texto transcrito desde el agente de voz del diseñador Angular
     * y retorna la acción estructurada que el frontend debe ejecutar.
     *
     * POST /api/voice/command
     * Body: { "text": "agrega nodo aprobación", "processId": "abc123" }
     */
    @PostMapping("/command")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> parseVoiceCommand(@RequestBody VoiceCommandRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "El campo 'text' es obligatorio"));
        }

        VoiceCommand command = voiceCommandService.parseCommand(
            request.getText(),
            request.getProcessId()
        );

        return ResponseEntity.ok(command);
    }
}
