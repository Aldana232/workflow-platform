package com.workflow.workflowplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.repository.DocumentRepository;
import com.workflow.workflowplatform.service.S3Service;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OnlyOfficeService {

    @Value("${onlyoffice.url}")
    private String onlyOfficeUrl;

    @Value("${onlyoffice.callback.url}")
    private String callbackUrl;

    @Value("${onlyoffice.jwt.secret}")
    private String jwtSecret;

    @Value("${app.public.url}")
    private String publicUrl;

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;

    /**
     * Genera la configuración que Angular necesita para inicializar el editor OnlyOffice
     */
    public Map<String, Object> generateEditorConfig(Document document, String userId, String userName, boolean canEdit) {
        String key = document.getOnlyOfficeKey() != null ? document.getOnlyOfficeKey() : UUID.randomUUID().toString().replace("-", "");

        // Actualizar key en BD si es nueva
        if (document.getOnlyOfficeKey() == null) {
            document.setOnlyOfficeKey(key);
            documentRepository.save(document);
        }

        String docType = getDocumentType(document.getMimeType(), document.getFileName());

        Map<String, Object> config = new HashMap<>();
        config.put("documentServerUrl", onlyOfficeUrl);

        Map<String, Object> documentConfig = new HashMap<>();
        documentConfig.put("fileType", getFileExtension(document.getFileName()));
        documentConfig.put("key", key);
        documentConfig.put("title", document.getFileName());
        // URL directa a nuestro backend (sin firma) — evita la incompatibilidad
        // del cliente HTTP de Node del Document Server con URLs presignadas de S3
        String rawUrl = publicUrl + "/api/documents/" + document.getId() + "/raw";
        documentConfig.put("url", rawUrl);

        Map<String, Object> permissionsConfig = new HashMap<>();
        permissionsConfig.put("edit", canEdit);
        permissionsConfig.put("download", true);
        permissionsConfig.put("print", true);
        documentConfig.put("permissions", permissionsConfig);

        config.put("document", documentConfig);
        config.put("documentType", docType);

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("callbackUrl", callbackUrl + "/" + document.getId());
        editorConfig.put("mode", canEdit ? "edit" : "view");
        editorConfig.put("lang", "es");

        Map<String, Object> userConfig = new HashMap<>();
        userConfig.put("id", userId);
        userConfig.put("name", userName);
        editorConfig.put("user", userConfig);

        config.put("editorConfig", editorConfig);

        String token = generateJwtToken(config);
        config.put("token", token);

        return config;
    }

    /**
     * Procesa el callback de OnlyOffice cuando el documento se guarda
     */
    public void processCallback(String documentId, Map<String, Object> callbackData) {
        int status = (int) callbackData.get("status");

        // status 2 = documento cerrado y guardado
        // status 6 = autosave
        if (status == 2 || status == 6) {
            String downloadUrl = (String) callbackData.get("url");
            if (downloadUrl != null) {
                try {
                    // FIX: OnlyOffice genera URLs con su host interno de Docker
                    // El backend corre fuera del contenedor, así que reescribimos al puerto expuesto
                    String fixedUrl = downloadUrl.replaceFirst("^https?://[^/]*", "http://localhost:8081");
                    URL url = new URL(fixedUrl);

                    // Descargar el documento editado desde OnlyOffice
                    InputStream inputStream = url.openStream();

                    // Buscar el documento en BD
                    Document document = documentRepository.findById(documentId)
                            .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

                    // Subir a S3 reemplazando el archivo anterior, en su key exacta original
                    String storedFileName = document.getStoredFileName();
                    String contentType = document.getMimeType();

                    s3Service.uploadFileAtKey(storedFileName, inputStream, contentType);

                    // Si es cierre final (status 2) regenerar key para nueva sesión
                    if (status == 2) {
                        document.setOnlyOfficeKey(UUID.randomUUID().toString().replace("-", ""));
                        documentRepository.save(document);
                    }

                } catch (Exception e) {
                    throw new RuntimeException("Error procesando callback OnlyOffice: " + e.getMessage());
                }
            }
        }
    }

    private String generateJwtToken(Map<String, Object> payload) {
        try {
            byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
            var key = Keys.hmacShaKeyFor(keyBytes);
            return Jwts.builder()
                    .setClaims(new HashMap<>(payload))
                    .signWith(key, SignatureAlgorithm.HS256)
                    .compact();
        } catch (Exception e) {
            return "";
        }
    }

    private String getDocumentType(String mimeType, String fileName) {
        if (mimeType == null) mimeType = "";
        if (mimeType.contains("word") || fileName.endsWith(".docx") || fileName.endsWith(".doc")) return "word";
        if (mimeType.contains("spreadsheet") || fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) return "cell";
        if (mimeType.contains("presentation") || fileName.endsWith(".pptx") || fileName.endsWith(".ppt")) return "slide";
        return "word";
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) return "docx";
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
