package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.DocumentEventRequest;
import com.workflow.workflowplatform.dto.DocumentUploadRequest;
import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.service.DocumentService;
import com.workflow.workflowplatform.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin
@Slf4j
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final S3Service s3Service;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("tramiteId") String tramiteId,
            @RequestParam("tramiteCode") String tramiteCode,
            @RequestParam("companyId") String companyId,
            @RequestParam("nodeId") String nodeId,
            @RequestParam("nodeName") String nodeName,
            @RequestParam("category") String category,
            @RequestParam(value = "description", required = false) String description) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();

            DocumentUploadRequest request = new DocumentUploadRequest();
            request.setTramiteId(tramiteId);
            request.setTramiteCode(tramiteCode);
            request.setCompanyId(companyId);
            request.setNodeId(nodeId);
            request.setNodeName(nodeName);
            request.setCategory(category);
            request.setDescription(description);

            Document saved = documentService.uploadDocument(file, request, userId, userId);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error al subir documento: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error al subir el documento: " + e.getMessage()));
        }
    }

    @GetMapping("/tramite/{tramiteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> getByTramite(@PathVariable String tramiteId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            List<Document> docs = documentService.getDocumentsByTramite(tramiteId, userId, userId);
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            log.error("Error al obtener documentos del trámite {}: {}", tramiteId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tramite/{tramiteId}/node/{nodeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> getByNode(@PathVariable String tramiteId,
                                       @PathVariable String nodeId) {
        try {
            List<Document> docs = documentService.getDocumentsByNode(tramiteId, nodeId);
            return ResponseEntity.ok(docs);
        } catch (Exception e) {
            log.error("Error al obtener documentos del nodo {}: {}", nodeId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{documentId}/event")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<?> addEvent(@PathVariable String documentId,
                                      @RequestBody DocumentEventRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            Document updated = documentService.addEvent(documentId, request, userId, userId);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Error al agregar evento al documento {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String documentId,
                                                   Authentication authentication) {
        try {
            String userId = authentication != null ? authentication.getName() : "anonymous";
            Document document = documentService.downloadDocument(documentId, userId, userId);
            byte[] fileBytes = s3Service.downloadFile(document.getStoredFileName());

            HttpHeaders headers = new HttpHeaders();
            String mimeType = document.getMimeType() != null ? document.getMimeType() : "application/octet-stream";
            headers.setContentType(MediaType.parseMediaType(mimeType));
            headers.setContentDispositionFormData("inline", document.getFileName());
            headers.setContentLength(fileBytes.length);

            return ResponseEntity.ok().headers(headers).body(fileBytes);
        } catch (Exception e) {
            log.error("Error al descargar documento {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteDocument(@PathVariable String documentId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String userId = auth.getName();
            Document deleted = documentService.deleteDocument(documentId, userId);
            return ResponseEntity.ok(deleted);
        } catch (Exception e) {
            log.error("Error al eliminar documento {}: {}", documentId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStats(@PathVariable String companyId) {
        try {
            Map<String, Object> stats = documentService.getDocumentStats(companyId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error al obtener estadísticas de documentos para company {}: {}", companyId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
