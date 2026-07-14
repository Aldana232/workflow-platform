package com.workflow.workflowplatform.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.Document;
import com.google.api.services.drive.Drive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleDocsService {

    private final ResourceLoader resourceLoader;

    @Value("${google.service-account.path:classpath:google-service-account.json}")
    private String serviceAccountPath;

    private Docs docsService;
    private Drive driveService;

    private void initializeServices() throws GeneralSecurityException, IOException {
        if (docsService != null && driveService != null) return;

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        Resource resource = resourceLoader.getResource(serviceAccountPath);
        InputStream inputStream = resource.getInputStream();

        GoogleCredential credential = GoogleCredential.fromStream(inputStream)
                .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive"));

        docsService = new Docs.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("workflow-saguapac")
                .build();

        driveService = new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("workflow-saguapac")
                .build();
    }

    public String createGoogleDoc(String fileName) throws GeneralSecurityException, IOException {
        initializeServices();

        Document doc = new Document();
        doc.setTitle(fileName);

        Document createdDoc = docsService.documents().create(doc).execute();
        String googleDocId = createdDoc.getDocumentId();

        log.info("Google Doc creado: {} con ID: {}", fileName, googleDocId);
        return googleDocId;
    }

    public String getGoogleDocUrl(String googleDocId) {
        return "https://docs.google.com/document/d/" + googleDocId + "/edit?usp=sharing";
    }
}
