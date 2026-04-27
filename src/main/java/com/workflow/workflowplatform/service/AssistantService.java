package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.model.ClienteInfo;
import com.workflow.workflowplatform.model.FormField;
import com.workflow.workflowplatform.model.FormSchema;
import com.workflow.workflowplatform.model.HistoryEntry;
import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.model.TaskSubmission;
import com.workflow.workflowplatform.model.Tramite;
import com.workflow.workflowplatform.repository.FormSchemaRepository;
import com.workflow.workflowplatform.repository.ProcessRepository;
import com.workflow.workflowplatform.repository.TaskSubmissionRepository;
import com.workflow.workflowplatform.repository.TramiteRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Construye el contexto completo del trámite (formulario, datos del cliente,
 * historial, submissions previas) y lo envía al LLM como system prompt.
 */
@Slf4j
@Service
public class AssistantService {

    // ─── Repositorios ────────────────────────────────────────────────────────

    @Autowired private TramiteRepository         tramiteRepository;
    @Autowired private FormSchemaRepository      formSchemaRepository;
    @Autowired private TaskSubmissionRepository  taskSubmissionRepository;
    @Autowired private ProcessRepository         processRepository;

    /** Inyectado sólo si openai.api.key está configurada (ver LangChain4jConfig) */
    @Autowired(required = false)
    private OpenAiChatModel chatModel;

    // ─── Constantes ───────────────────────────────────────────────────────────

    private static final int MAX_HISTORY   = 10;   // entradas de historial a mostrar
    private static final int MAX_PREV_SUBS = 5;    // submissions anteriores a incluir
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ─── API pública ─────────────────────────────────────────────────────────

    public String ask(String question, String tramiteId, String nodeId) {
        if (chatModel == null) {
            throw new IllegalStateException(
                "El asistente IA no está configurado. " +
                "Solicite al administrador que configure la variable OPENAI_API_KEY.");
        }

        // 1. Sanitizar pregunta (anti prompt-injection básico)
        String safeQuestion = sanitize(question);

        // 2. Cargar trámite
        Tramite tramite = tramiteRepository.findById(tramiteId)
            .orElseThrow(() -> new RuntimeException("Trámite no encontrado: " + tramiteId));

        // 3. Resolver nodo efectivo
        String effectiveNodeId = (nodeId != null && !nodeId.isBlank())
            ? nodeId : tramite.getCurrentNodeId();

        // 4. Cargar proceso (para etiquetas de nodos)
        Process process = processRepository.findById(tramite.getProcessId()).orElse(null);

        // 5. Cargar FormSchema del nodo actual
        FormSchema formSchema = formSchemaRepository
            .findByProcessIdAndNodeId(tramite.getProcessId(), effectiveNodeId)
            .orElse(null);

        // 6. Cargar submissions completadas anteriores (ordenadas por fecha)
        List<TaskSubmission> prevSubmissions = taskSubmissionRepository
            .findByTramiteId(tramiteId).stream()
            .filter(s -> s.getCompletedAt() != null)
            .sorted(Comparator.comparing(TaskSubmission::getCompletedAt))
            .limit(MAX_PREV_SUBS)
            .collect(Collectors.toList());

        // 7. Construir system prompt dinámico
        String systemPrompt = buildSystemPrompt(tramite, process, effectiveNodeId, formSchema, prevSubmissions);

        log.debug("[Assistant] tramite={} node={} promptChars={}", tramiteId, effectiveNodeId, systemPrompt.length());

        // 8. Llamar al LLM directamente (evita problemas de @SystemMessage dinámico en LangChain4j 0.36.x)
        try {
            List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(safeQuestion)
            );
            String answer = chatModel.generate(messages).content().text();
            log.debug("[Assistant] Respuesta generada correctamente ({} chars)", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("[Assistant] Error LLM: {}", e.getMessage(), e);
            throw new RuntimeException("Error al consultar la IA: " + e.getMessage());
        }
    }

    // ─── Construcción del system prompt ─────────────────────────────────────

    private String buildSystemPrompt(Tramite tramite, Process process, String nodeId,
                                     FormSchema formSchema, List<TaskSubmission> prevSubmissions) {
        StringBuilder sb = new StringBuilder();

        // Rol e instrucciones (estáticas)
        sb.append("""
            Eres un asistente experto que ayuda a funcionarios de SAGUAPAC \
            (empresa de agua potable y alcantarillado de Cochabamba, Bolivia) \
            a completar formularios de trámites BPMN.

            INSTRUCCIONES OBLIGATORIAS:
            - Responde siempre en español, de forma clara y concisa.
            - Usa ÚNICAMENTE la información del contexto que se te proporciona a continuación.
            - NUNCA inventes datos del cliente, fechas, estados ni valores de formulario.
            - Si te preguntan algo que no está en el contexto, responde: "No tengo esa información en el contexto del trámite."
            - Si el funcionario necesita completar un campo, explícale qué es, por qué importa y cómo llenarlo.
            - No repitas bloques de información completos; sé directo y útil.

            """);

        // Encabezado del trámite
        String processName = process != null ? process.getName() : "Proceso desconocido";
        String nodeLabel   = findNodeLabel(process, nodeId);

        sb.append("════════════════════════════════════════════\n");
        sb.append("PROCESO        : ").append(processName).append("\n");
        sb.append("CÓDIGO TRÁMITE : ").append(tramite.getCode()).append("\n");
        sb.append("ESTADO         : ").append(tramite.getStatus()).append("\n");
        sb.append("ETAPA ACTUAL   : ").append(nodeLabel).append("\n");
        sb.append("════════════════════════════════════════════\n\n");

        // Datos del cliente
        appendClienteInfo(sb, tramite.getClienteInfo());

        // Formulario del nodo actual
        appendFormSchema(sb, formSchema, nodeLabel);

        // Datos ya ingresados en etapas anteriores
        appendPreviousSubmissions(sb, prevSubmissions, process);

        // Historial de eventos del trámite
        appendHistory(sb, tramite);

        return sb.toString();
    }

    // ─── Secciones del prompt ────────────────────────────────────────────────

    private void appendClienteInfo(StringBuilder sb, ClienteInfo c) {
        sb.append("── DATOS DEL CLIENTE ─────────────────────────\n");
        if (c == null) {
            sb.append("Sin información del cliente registrada.\n\n");
            return;
        }
        appendLine(sb, "Nombre",    c.getNombre());
        appendLine(sb, "CI / NIT",  c.getCi());
        appendLine(sb, "Teléfono",  c.getTelefono());
        appendLine(sb, "Email",     c.getEmail());
        appendLine(sb, "Dirección", c.getDireccion());
        sb.append("\n");
    }

    private void appendFormSchema(StringBuilder sb, FormSchema schema, String nodeLabel) {
        sb.append("── FORMULARIO QUE DEBE COMPLETARSE: ").append(nodeLabel).append(" ──\n");
        if (schema == null || schema.getFields() == null || schema.getFields().isEmpty()) {
            sb.append("Este nodo no tiene campos de formulario definidos.\n\n");
            return;
        }
        for (FormField f : schema.getFields()) {
            String label = f.getLabel() != null ? f.getLabel() : f.getName();
            sb.append("• ").append(label)
              .append("  [").append(f.getType()).append("]")
              .append(Boolean.TRUE.equals(f.getRequired()) ? "  ✱ obligatorio" : "  (opcional)");

            if (f.getOptions() != null && !f.getOptions().isEmpty()) {
                sb.append("  |  Opciones: ").append(String.join(", ", f.getOptions()));
            }
            if (f.getPlaceholder() != null && !f.getPlaceholder().isBlank()) {
                sb.append("  |  Ej: ").append(f.getPlaceholder());
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendPreviousSubmissions(StringBuilder sb, List<TaskSubmission> subs, Process process) {
        if (subs.isEmpty()) return;

        sb.append("── DATOS YA INGRESADOS EN ETAPAS ANTERIORES ─\n");
        for (TaskSubmission s : subs) {
            String label = findNodeLabel(process, s.getNodeId());
            String ts    = s.getCompletedAt() != null ? s.getCompletedAt().format(FMT) : "—";
            sb.append("Etapa: ").append(label).append("  (completada: ").append(ts).append(")\n");

            if (s.getFormData() != null) {
                s.getFormData().forEach((k, v) -> {
                    if (v != null && !v.toString().isBlank()) {
                        sb.append("  ▸ ").append(k).append(": ").append(v).append("\n");
                    }
                });
            }
            if (s.getComments() != null && !s.getComments().isBlank()) {
                sb.append("  ▸ Observaciones: ").append(s.getComments()).append("\n");
            }
        }
        sb.append("\n");
    }

    private void appendHistory(StringBuilder sb, Tramite tramite) {
        sb.append("── HISTORIAL DE EVENTOS DEL TRÁMITE ──────────\n");
        if (tramite.getHistory() == null || tramite.getHistory().isEmpty()) {
            sb.append("Sin historial registrado.\n\n");
            return;
        }

        List<HistoryEntry> entries = tramite.getHistory();
        boolean truncated = entries.size() > MAX_HISTORY;
        if (truncated) {
            sb.append("(Mostrando los últimos ").append(MAX_HISTORY)
              .append(" de ").append(entries.size()).append(" eventos)\n");
            entries = entries.subList(entries.size() - MAX_HISTORY, entries.size());
        }

        int n = 1;
        for (HistoryEntry h : entries) {
            sb.append(n++).append(". ");
            if (h.getTimestamp() != null) sb.append("[").append(h.getTimestamp().format(FMT)).append("] ");
            sb.append(h.getAction() != null ? h.getAction() : "—");
            if (h.getNodeId() != null)  sb.append(" | nodo: ").append(h.getNodeId());
            if (h.getDetails() != null && !h.getDetails().isBlank()) sb.append(" | ").append(h.getDetails());
            sb.append("\n");
        }
        sb.append("\n");
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private String findNodeLabel(Process process, String nodeId) {
        if (process == null || process.getNodes() == null || nodeId == null) return nodeId;
        return process.getNodes().stream()
            .filter(n -> nodeId.equals(n.getId()))
            .findFirst()
            .map(n -> n.getLabel() != null ? n.getLabel() : nodeId)
            .orElse(nodeId);
    }

    private void appendLine(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(String.format("%-10s: %s%n", key, value));
        }
    }

    /**
     * Previene prompt injection básico:
     * - Limita a 500 caracteres
     * - Elimina patrones de redirección de rol conocidos
     */
    private String sanitize(String input) {
        if (input == null) return "";
        String safe = input.strip();
        if (safe.length() > 500) safe = safe.substring(0, 500);
        safe = safe.replaceAll(
            "(?i)(ignore (previous|all|above|instructions)|forget (everything|all)|" +
            "you are now|pretend (you are|to be)|override|disregard|" +
            "system prompt|\\[INST]|<\\|im_start\\|>|<\\|im_end\\|>)",
            "[ELIMINADO]"
        );
        return safe.strip();
    }
}
