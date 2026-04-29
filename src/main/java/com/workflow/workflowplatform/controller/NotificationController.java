package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.RegisterTokenRequest;
import com.workflow.workflowplatform.model.FcmToken;
import com.workflow.workflowplatform.repository.FcmTokenRepository;
import com.workflow.workflowplatform.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final FcmTokenRepository fcmTokenRepository;
    private final PushNotificationService pushNotificationService;

    @PostMapping("/register-token")
    public ResponseEntity<String> registerToken(
            @RequestBody @Valid RegisterTokenRequest request) {

        Optional<FcmToken> existing = fcmTokenRepository
                .findByTramiteCodeAndFcmToken(
                        request.getTramiteCode(),
                        request.getFcmToken()
                );

        if (existing.isPresent()) {
            FcmToken token = existing.get();
            token.setUpdatedAt(LocalDateTime.now());
            fcmTokenRepository.save(token);
            return ResponseEntity.ok("Token actualizado correctamente");
        }

        FcmToken newToken = FcmToken.builder()
                .tramiteCode(request.getTramiteCode())
                .fcmToken(request.getFcmToken())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        fcmTokenRepository.save(newToken);
        log.info("Token FCM registrado para trámite: " + request.getTramiteCode());
        return ResponseEntity.ok("Token registrado correctamente");
    }

    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> testNotification(
            @RequestBody Map<String, String> body) {

        String fcmToken = body.get("fcmToken");
        pushNotificationService.sendTestNotification(fcmToken);
        return ResponseEntity.ok("Notificación de prueba enviada");
    }

    @DeleteMapping("/token/{fcmToken}")
    public ResponseEntity<String> deleteToken(@PathVariable String fcmToken) {
        fcmTokenRepository.deleteByFcmToken(fcmToken);
        return ResponseEntity.ok("Token eliminado");
    }
}
