package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.model.FormField;
import com.workflow.workflowplatform.model.FormSchema;
import com.workflow.workflowplatform.model.Node;
import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.repository.ProcessRepository;
import com.workflow.workflowplatform.service.FormSchemaService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/form-schemas")
@RequiredArgsConstructor
public class FormSchemaController {

    private final FormSchemaService formSchemaService;
    private final ProcessRepository processRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<FormSchema> createOrUpdate(@RequestBody FormSchemaRequest request) {
        FormSchema schema = formSchemaService.createOrUpdateSchema(
                request.getProcessId(), request.getNodeId(), request.getFields());
        return ResponseEntity.ok(schema);
    }

    /**
     * Importa todos los formularios de un proceso de una sola vez.
     * Recibe processId + mapa de { "Nombre del nodo" → [campos] }
     * y los asocia automáticamente a los nodeIds correctos del proceso.
     *
     * POST /api/form-schemas/import
     * Body: { "processId": "...", "nodeSchemas": [{ "nodeName": "Atención al cliente", "fields": [...] }] }
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> importByNodeName(
            @RequestBody ImportRequest request) {

        Process process = processRepository.findById(request.getProcessId())
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + request.getProcessId()));

        List<String> saved  = new ArrayList<>();
        List<String> missed = new ArrayList<>();

        if (request.getNodeSchemas() != null) {
            for (NodeSchema ns : request.getNodeSchemas()) {
                String targetName = ns.getNodeName().trim().toLowerCase();

                Node matched = (process.getNodes() == null) ? null : process.getNodes().stream()
                        .filter(n -> n.getLabel() != null &&
                                     n.getLabel().trim().toLowerCase().contains(targetName))
                        .findFirst().orElse(null);

                if (matched == null) {
                    missed.add(ns.getNodeName());
                } else {
                    formSchemaService.createOrUpdateSchema(
                            process.getId(), matched.getId(), ns.getFields());
                    saved.add(ns.getNodeName() + " → " + matched.getId());
                }
            }
        }

        return ResponseEntity.ok(Map.of("saved", saved, "notMatched", missed));
    }

    @GetMapping("/process/{processId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<List<FormSchema>> getByProcess(@PathVariable String processId) {
        return ResponseEntity.ok(formSchemaService.getSchemasByProcess(processId));
    }

    @GetMapping("/node")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<FormSchema> getByNode(
            @RequestParam String processId,
            @RequestParam String nodeId) {
        return ResponseEntity.ok(formSchemaService.getSchemaByNode(processId, nodeId));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        formSchemaService.deleteSchema(id);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    static class FormSchemaRequest {
        private String processId;
        private String nodeId;
        private List<FormField> fields;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class ImportRequest {
        private String processId;
        private List<NodeSchema> nodeSchemas;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    static class NodeSchema {
        private String nodeName;
        private List<FormField> fields;
    }
}
