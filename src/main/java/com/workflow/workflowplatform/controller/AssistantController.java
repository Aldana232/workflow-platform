package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.ApiResponseDTO;
import com.workflow.workflowplatform.dto.AssistantRequest;
import com.workflow.workflowplatform.service.AssistantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * POST /api/assistant/ask
     *
     * Permite a un funcionario hacer una pregunta sobre el trámite/formulario en curso.
     * El asistente responde usando el contexto completo cargado desde la base de datos.
     */
    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('FUNCIONARIO', 'ADMIN', 'SUPERADMIN')")
    public ResponseEntity<ApiResponseDTO<String>> ask(@Valid @RequestBody AssistantRequest request) {
        log.info("[Assistant] Pregunta recibida | tramite={} node={}",
            request.getTramiteId(), request.getNodeId());

        try {
            String answer = assistantService.ask(
                request.getQuestion(),
                request.getTramiteId(),
                request.getNodeId()
            );
            return ResponseEntity.ok(ApiResponseDTO.success(answer, "Respuesta generada"));

        } catch (IllegalStateException e) {
            // IA no configurada
            return ResponseEntity.status(503)
                .body(ApiResponseDTO.error(e.getMessage(), "AI_NOT_CONFIGURED"));

        } catch (RuntimeException e) {
            log.error("[Assistant] Error al procesar pregunta: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(ApiResponseDTO.error(e.getMessage(), "ASSISTANT_ERROR"));
        }
    }
}
