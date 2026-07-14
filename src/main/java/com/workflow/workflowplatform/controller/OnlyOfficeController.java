package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.OnlyOfficeConfigDTO;
import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.repository.DocumentRepository;
import com.workflow.workflowplatform.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/onlyoffice")
@CrossOrigin
@Slf4j
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final RestTemplate restTemplate;

    @GetMapping("/config/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<OnlyOfficeConfigDTO> getConfig(@PathVariable String documentId,
                                                          Authentication authentication) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

        String fileType = extractFileType(document.getFileName());

        OnlyOfficeConfigDTO config = OnlyOfficeConfigDTO.builder()
                .documentType("word")
                .document(OnlyOfficeConfigDTO.Document.builder()
                        .fileType(fileType)
                        .key(documentId + "_" + System.currentTimeMillis())
                        .title(document.getFileName())
                        .url(resolveOnlyOfficeUrl(document))
                        .build())
                .editorConfig(OnlyOfficeConfigDTO.EditorConfig.builder()
                        .callbackUrl("https://api.workflow-demo.site/api/onlyoffice/callback/" + documentId)
                        .lang("es")
                        .mode("edit")
                        .user(OnlyOfficeConfigDTO.User.builder()
                                .id(authentication.getName())
                                .name(authentication.getName())
                                .build())
                        .build())
                .build();

        return ResponseEntity.ok(config);
    }

    @PostMapping("/callback/{documentId}")
    public ResponseEntity<Map<String, Integer>> callback(@PathVariable String documentId,
                                                          @RequestBody Map<String, Object> body) {
        try {
            Integer status = (Integer) body.get("status");
            if (status != null && status == 2) {
                String url = (String) body.get("url");
                Document document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

                byte[] fileBytes = restTemplate.getForObject(url, byte[].class);
                String fileUrl = s3Service.uploadFile(
                        document.getStoredFileName(),
                        new ByteArrayInputStream(fileBytes),
                        document.getMimeType(),
                        null
                );

                document.setFileUrl(fileUrl);
                documentRepository.save(document);
                log.info("Documento {} actualizado desde OnlyOffice", documentId);
            }
        } catch (Exception e) {
            log.error("Error en callback de OnlyOffice para documento {}: {}", documentId, e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of("error", 0));
    }

    /**
     * document.fileUrl guarda la URL cruda de S3 (bucket privado, sin firma) o una URL
     * local servida por FileController. OnlyOffice Document Server descarga el archivo
     * por su cuenta, así que una URL de S3 sin firmar le devuelve 403. Para archivos en
     * S3 generamos una URL prefirmada usando la key real (extraída de la propia fileUrl,
     * ya que incluye la carpeta por rol que no se persiste por separado).
     */
    private String resolveOnlyOfficeUrl(Document document) {
        String fileUrl = document.getFileUrl();
        String marker = ".amazonaws.com/";
        int idx = fileUrl != null ? fileUrl.indexOf(marker) : -1;
        if (idx == -1) {
            return fileUrl;
        }
        String s3Key = fileUrl.substring(idx + marker.length());
        return s3Service.generatePresignedUrl(s3Key, 60);
    }

    private String extractFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "docx";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
