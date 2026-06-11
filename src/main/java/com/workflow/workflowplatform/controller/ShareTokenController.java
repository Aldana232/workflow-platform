package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.model.ShareToken;
import com.workflow.workflowplatform.service.ShareTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/share")
@CrossOrigin
@Slf4j
@RequiredArgsConstructor
public class ShareTokenController {

    private final ShareTokenService shareTokenService;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> generateToken(@RequestBody Map<String, String> body) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            String tramiteId = body.get("tramiteId");
            String tramiteCode = body.get("tramiteCode");
            ShareToken shareToken = shareTokenService.generateToken(tramiteId, tramiteCode, userId, userId);
            String shareUrl = "https://workflow-demo.site/shared/" + shareToken.getToken();
            return ResponseEntity.ok(Map.of(
                    "token", shareToken.getToken(),
                    "shareUrl", shareUrl,
                    "expiresAt", shareToken.getExpiresAt().toString()
            ));
        } catch (Exception e) {
            log.error("Error al generar token compartible: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/validate/{token}")
    public ResponseEntity<?> validateToken(@PathVariable String token) {
        try {
            ShareToken shareToken = shareTokenService.validateToken(token);
            List<Document> documents = shareTokenService.getDocumentsForSharedToken(shareToken.getTramiteId());
            return ResponseEntity.ok(Map.of(
                    "tramiteId", shareToken.getTramiteId(),
                    "tramiteCode", shareToken.getTramiteCode(),
                    "expiresAt", shareToken.getExpiresAt().toString(),
                    "accessCount", shareToken.getAccessCount(),
                    "documents", documents
            ));
        } catch (Exception e) {
            log.warn("Token inválido {}: {}", token, e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tokens/{tramiteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> getTokensByTramite(@PathVariable String tramiteId) {
        try {
            List<ShareToken> tokens = shareTokenService.getTokensByTramite(tramiteId);
            return ResponseEntity.ok(tokens);
        } catch (Exception e) {
            log.error("Error al listar tokens del trámite {}: {}", tramiteId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/revoke/{tokenId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> revokeToken(@PathVariable String tokenId) {
        try {
            ShareToken revoked = shareTokenService.revokeToken(tokenId);
            return ResponseEntity.ok(revoked);
        } catch (Exception e) {
            log.error("Error al revocar token {}: {}", tokenId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
