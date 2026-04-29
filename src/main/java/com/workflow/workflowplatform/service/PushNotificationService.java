package com.workflow.workflowplatform.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.workflow.workflowplatform.model.FcmToken;
import com.workflow.workflowplatform.repository.FcmTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class PushNotificationService {

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    public void sendToTramite(String tramiteCode, String title, String body) {
        List<FcmToken> tokens = fcmTokenRepository.findByTramiteCode(tramiteCode);

        if (tokens.isEmpty()) {
            log.info("No hay tokens registrados para: " + tramiteCode);
            return;
        }

        for (FcmToken tokenDoc : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(tokenDoc.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putData("tramiteCode", tramiteCode)
                        .putData("type", "TRAMITE_UPDATE")
                        .build();

                String response = FirebaseMessaging.getInstance().send(message);
                log.info("Push enviado exitosamente: " + response);

            } catch (FirebaseMessagingException e) {
                log.error("Error enviando push: " + e.getMessage());
                String errorCode = e.getMessagingErrorCode() != null ?
                        e.getMessagingErrorCode().name() : "";
                if (errorCode.equals("UNREGISTERED") || errorCode.equals("INVALID_ARGUMENT")) {
                    log.info("Token inválido, eliminando: " + tokenDoc.getFcmToken());
                    fcmTokenRepository.delete(tokenDoc);
                }
            }
        }
    }

    public void sendTestNotification(String fcmToken) {
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle("🔔 Prueba de notificación")
                            .setBody("Las notificaciones push están funcionando correctamente")
                            .build())
                    .putData("type", "TEST")
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Notificación de prueba enviada: " + response);

        } catch (FirebaseMessagingException e) {
            log.error("Error en prueba: " + e.getMessage());
            throw new RuntimeException("Error enviando notificación de prueba: " + e.getMessage());
        }
    }
}
