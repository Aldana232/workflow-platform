package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.model.FormField;
import com.workflow.workflowplatform.model.FormSchema;
import com.workflow.workflowplatform.repository.FormSchemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormSchemaService {

    private final FormSchemaRepository formSchemaRepository;

    public FormSchema createOrUpdateSchema(String processId, String nodeId, List<FormField> fields) {
        FormSchema schema = formSchemaRepository.findByProcessIdAndNodeId(processId, nodeId)
                .orElse(FormSchema.builder()
                        .processId(processId)
                        .nodeId(nodeId)
                        .createdAt(LocalDateTime.now())
                        .build());

        // Garantizar que cada campo tenga id y name válidos para el frontend
        if (fields != null) {
            for (FormField f : fields) {
                if (f.getId() == null || f.getId().isBlank()) {
                    f.setId(toSlug(f.getLabel()));
                }
                if (f.getName() == null || f.getName().isBlank()) {
                    f.setName(f.getId());
                }
            }
        }

        schema.setFields(fields);
        schema.setUpdatedAt(LocalDateTime.now());
        return formSchemaRepository.save(schema);
    }

    public FormSchema getSchemaByNode(String processId, String nodeId) {
        return formSchemaRepository.findByProcessIdAndNodeId(processId, nodeId)
                .orElse(FormSchema.builder()
                        .processId(processId)
                        .nodeId(nodeId)
                        .fields(new ArrayList<>())
                        .build());
    }

    public List<FormSchema> getSchemasByProcess(String processId) {
        return formSchemaRepository.findByProcessId(processId);
    }

    public void deleteSchema(String id) {
        formSchemaRepository.deleteById(id);
    }

    /** Convierte una etiqueta en un identificador snake_case ASCII */
    private String toSlug(String label) {
        if (label == null || label.isBlank()) return "campo_" + System.currentTimeMillis();
        String s = label.toLowerCase(java.util.Locale.ROOT)
                .replace('á', 'a').replace('é', 'e').replace('í', 'i')
                .replace('ó', 'o').replace('ú', 'u').replace('ñ', 'n')
                .replace('ä', 'a').replace('ë', 'e').replace('ï', 'i')
                .replace('ö', 'o').replace('ü', 'u')
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "campo_" + System.currentTimeMillis() : s;
    }
}
