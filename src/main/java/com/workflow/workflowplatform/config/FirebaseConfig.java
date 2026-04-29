package com.workflow.workflowplatform.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        InputStream serviceAccount = getClass()
                .getResourceAsStream("/firebase-service-account.json");

        if (serviceAccount == null) {
            throw new IllegalStateException(
                    "No se encontró firebase-service-account.json");
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase inicializado correctamente como @Bean");
            return app;
        }

        log.info("Firebase ya estaba inicializado");
        return FirebaseApp.getInstance();
    }
}
