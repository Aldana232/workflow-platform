package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.dto.DocumentEventRequest;
import com.workflow.workflowplatform.dto.DocumentUploadRequest;
import com.workflow.workflowplatform.model.Department;
import com.workflow.workflowplatform.model.Document;
import com.workflow.workflowplatform.model.DocumentEvent;
import com.workflow.workflowplatform.model.User;
import com.workflow.workflowplatform.repository.DepartmentRepository;
import com.workflow.workflowplatform.repository.DocumentRepository;
import com.workflow.workflowplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final S3Service s3Service;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${server.port:8080}")
    private String serverPort;

    public Document uploadDocument(MultipartFile file, DocumentUploadRequest request,
                                   String userId, String userName,
                                   Authentication authentication) throws IOException {
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "archivo";
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf("."))
                : "";
        String storedFileName = UUID.randomUUID() + ext;

        String userRole = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("FUNCIONARIO");

        String fileUrl = resolveFileUrl(file, storedFileName, userRole);

        User uploader = userRepository.findByEmail(userId).orElse(null);
        String fullName = (uploader != null && uploader.getFirstName() != null)
                ? (uploader.getFirstName() + " " + uploader.getLastName()).trim()
                : userName;
        String deptId   = (uploader != null) ? uploader.getDepartmentId() : null;
        String deptName = null;
        if (deptId != null) {
            deptName = departmentRepository.findById(deptId)
                    .map(Department::getName).orElse(null);
        }

        DocumentEvent uploadEvent = DocumentEvent.builder()
                .eventType("UPLOAD")
                .userId(userId)
                .userName(fullName)
                .description("Documento subido: " + originalName)
                .timestamp(LocalDateTime.now())
                .build();

        Document document = Document.builder()
                .tramiteId(request.getTramiteId())
                .tramiteCode(request.getTramiteCode())
                .companyId(request.getCompanyId())
                .nodeId(request.getNodeId())
                .nodeName(request.getNodeName())
                .uploadedBy(userId)
                .uploadedByName(fullName)
                .departmentId(deptId)
                .departmentName(deptName)
                .fileName(originalName)
                .storedFileName(storedFileName)
                .fileUrl(fileUrl)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .category(request.getCategory())
                .description(request.getDescription())
                .tags(request.getTags() != null ? request.getTags() : new ArrayList<>())
                .events(new ArrayList<>(List.of(uploadEvent)))
                .version(1)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return documentRepository.save(document);
    }

    /** Intenta subir a S3 en la carpeta del rol; si falla guarda localmente. */
    private String resolveFileUrl(MultipartFile file, String storedFileName, String userRole) throws IOException {
        try {
            String url = s3Service.uploadFile(storedFileName, file.getInputStream(), file.getContentType(), userRole);
            log.info("Archivo almacenado en S3: {}", url);
            return url;
        } catch (Exception e) {
            log.warn("S3 no disponible ({}), guardando localmente.", e.getMessage());
            return saveLocally(file, storedFileName);
        }
    }

    private String saveLocally(MultipartFile file, String storedFileName) throws IOException {
        Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path dest = dir.resolve(storedFileName);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        String url = "http://localhost:" + serverPort + "/api/files/" + storedFileName;
        log.info("Archivo almacenado localmente: {}", url);
        return url;
    }

    public List<Document> getDocumentsByTramite(String tramiteId, String userId, String userName) {
        List<Document> documents = documentRepository.findByTramiteIdAndActiveTrue(tramiteId);

        documents.forEach(doc -> {
            DocumentEvent viewEvent = DocumentEvent.builder()
                    .eventType("VIEW")
                    .userId(userId)
                    .userName(userName)
                    .description("Documento visualizado")
                    .timestamp(LocalDateTime.now())
                    .build();
            if (doc.getEvents() == null) {
                doc.setEvents(new ArrayList<>());
            }
            doc.getEvents().add(viewEvent);
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);
        });

        return documents;
    }

    public List<Document> getDocumentsByNode(String tramiteId, String nodeId) {
        return documentRepository.findByTramiteIdAndNodeIdAndActiveTrue(tramiteId, nodeId);
    }

    public Document addEvent(String documentId, DocumentEventRequest request,
                             String userId, String userName) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

        DocumentEvent event = DocumentEvent.builder()
                .eventType(request.getEventType())
                .userId(userId)
                .userName(userName)
                .description(request.getDescription())
                .timestamp(LocalDateTime.now())
                .build();

        document.getEvents().add(event);
        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    public Document downloadDocument(String documentId, String userId, String userName) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

        DocumentEvent downloadEvent = DocumentEvent.builder()
                .eventType("DOWNLOAD")
                .userId(userId)
                .userName(userName)
                .description("Documento descargado")
                .timestamp(LocalDateTime.now())
                .build();

        document.getEvents().add(downloadEvent);
        document.setUpdatedAt(LocalDateTime.now());
        return documentRepository.save(document);
    }

    public Document deleteDocument(String documentId, String userId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Documento no encontrado: " + documentId));

        document.setActive(false);
        document.setUpdatedAt(LocalDateTime.now());

        DocumentEvent deleteEvent = DocumentEvent.builder()
                .eventType("DELETE")
                .userId(userId)
                .userName(userId)
                .description("Documento eliminado (soft delete)")
                .timestamp(LocalDateTime.now())
                .build();

        document.getEvents().add(deleteEvent);
        return documentRepository.save(document);
    }

    public Map<String, Object> getDocumentStats(String companyId) {
        List<Document> all = documentRepository.findByCompanyIdAndActiveTrue(companyId);

        Map<String, Long> byCategory = all.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getCategory() != null ? d.getCategory() : "SIN_CATEGORIA",
                        Collectors.counting()
                ));

        List<Document> recentUploads = all.stream()
                .sorted(Comparator.comparing(Document::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDocuments", (long) all.size());
        stats.put("documentsByCategory", byCategory);
        stats.put("recentUploads", recentUploads);
        return stats;
    }
}
