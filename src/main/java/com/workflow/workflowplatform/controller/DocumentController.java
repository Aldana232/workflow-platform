package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.DocumentEventRequest;
import com.workflow.workflowplatform.dto.DocumentUploadRequest;
import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.service.DocumentService;
import com.workflow.workflowplatform.service.OnlyOfficeService;
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

import java.util.ArrayList;
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
    private final OnlyOfficeService onlyOfficeService;

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

            Document saved = documentService.uploadDocument(file, request, userId, userId, auth);
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

    // ─── OnlyOffice Colaborativo ───────────────────────────────────────────────

    /**
     * Admin habilita modo colaborativo en un documento y asigna permisos
     */
    @PutMapping("/{documentId}/collab/enable")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> enableCollaborativeMode(
            @PathVariable String documentId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        try {
            Document document = documentService.getDocumentById(documentId);
            document.setCollaborativeMode(true);

            @SuppressWarnings("unchecked")
            List<String> editorIds = (List<String>) body.getOrDefault("editorIds", new ArrayList<>());
            @SuppressWarnings("unchecked")
            List<String> viewerIds = (List<String>) body.getOrDefault("viewerIds", new ArrayList<>());

            document.setEditorIds(editorIds);
            document.setViewerIds(viewerIds);
            documentService.saveDocument(document);

            return ResponseEntity.ok(Map.of("message", "Modo colaborativo habilitado", "documentId", documentId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtiene la configuración del editor OnlyOffice para un documento
     */
    @GetMapping("/{documentId}/collab/config")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','FUNCIONARIO')")
    public ResponseEntity<?> getEditorConfig(
            @PathVariable String documentId,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            Document document = documentService.getDocumentById(documentId);

            if (!document.isCollaborativeMode()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Este documento no tiene modo colaborativo habilitado"));
            }

            boolean canEdit = document.getEditorIds().contains(userId) ||
                    authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPERADMIN"));

            boolean canAccess = canEdit || document.getViewerIds().contains(userId);
            if (!canAccess) {
                return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para acceder a este documento"));
            }

            // Obtener nombre del usuario
            String userName = userId;
            Map<String, Object> config = onlyOfficeService.generateEditorConfig(document, userId, userName, canEdit);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Callback de OnlyOffice — guarda el documento editado en S3
     * Este endpoint debe ser público (sin autenticación) porque lo llama OnlyOffice
     */
    @PostMapping("/callback/{documentId}")
    public ResponseEntity<?> onlyOfficeCallback(
            @PathVariable String documentId,
            @RequestBody Map<String, Object> callbackData) {
        try {
            onlyOfficeService.processCallback(documentId, callbackData);
        } catch (Exception e) {
            log.error("Error en callback OnlyOffice para doc {}: {}", documentId, e.getMessage());
        }
        // OnlyOffice requiere SIEMPRE {"error": 0} — cualquier otro valor causa reintentos
        return ResponseEntity.ok(Map.of("error", 0));
    }

    /**
     * Lista documentos colaborativos de un trámite accesibles para el usuario actual
     */
    @GetMapping("/tramite/{tramiteId}/collab")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN','FUNCIONARIO')")
    public ResponseEntity<?> getCollaborativeDocuments(
            @PathVariable String tramiteId,
            Authentication authentication) {
        try {
            String userId = authentication.getName();
            List<Document> allDocs = documentService.getDocumentsByTramite(tramiteId, userId, userId);
            List<Document> collabDocs = allDocs.stream()
                    .filter(Document::isCollaborativeMode)
                    .filter(doc -> doc.getEditorIds().contains(userId) ||
                            doc.getViewerIds().contains(userId) ||
                            authentication.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") ||
                                            a.getAuthority().equals("ROLE_SUPERADMIN")))
                    .toList();
            return ResponseEntity.ok(collabDocs);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
