package com.workflow.workflowplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.workflowplatform.dto.VoiceCommand;
import com.workflow.workflowplatform.model.enums.NodeType;
import com.workflow.workflowplatform.model.enums.VoiceAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte texto libre (transcripción de voz) en un VoiceCommand estructurado.
 *
 * Estrategia (con AI configurada):
 *  1. Enviar texto al LLM via VoiceCommandAiService
 *  2. Parsear JSON de respuesta → VoiceCommand
 *  3. Si falla o retorna UNKNOWN → fallback regex
 *
 * Estrategia (sin AI / fallback):
 *  1. normalizar()  — minúsculas, sin tildes, sin puntuación
 *  2. Probar reglas regex en orden de especificidad
 *  3. Retornar UNKNOWN si ninguna regla coincide
 */
@Slf4j
@Service
public class VoiceCommandService {

    // Inyectado sólo si la API key está configurada (ConditionalOnProperty en LangChain4jConfig)
    @Autowired(required = false)
    private VoiceCommandAiService aiService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ═══════════════════════════════════════════════════════════════════════════
    // PATRONES COMPILADOS (compilar una sola vez al cargar la clase)
    // ═══════════════════════════════════════════════════════════════════════════

    // Disparadores de agregar elemento
    private static final Pattern ADD_TRIGGER = Pattern.compile(
        "^(agrega|anade|agregar|anadir|inserta|insertar|crea|crear)\\b"
    );

    // Nombre tras el tipo de elemento: "tarea de usuario [llamado]? NOMBRE"
    private static final Pattern NAME_AFTER_TYPE = Pattern.compile(
        "(?:nodo|tarea(?:\\s+de\\s+(?:usuario|servicio|envio|recepcion|script))?|" +
        "evento(?:\\s+de\\s+(?:inicio|fin|intermedio))?|" +
        "gateway(?:\\s+(?:exclusivo|paralelo|inclusivo))?|" +
        "compuerta(?:\\s+(?:exclusiva|paralela|inclusiva))?|" +
        "sub\\s*proceso)" +
        "\\s+(?:llamado\\s+|llamada\\s+)?(.+)"
    );

    // CONNECT_NODES: "conecta INICIO con APROBACION"
    private static final Pattern CONNECT = Pattern.compile(
        "(?:conecta|une|conectar|unir)\\s+(.+?)\\s+con\\s+(.+)"
    );

    // DELETE_NODE: "elimina el nodo revision tecnica"
    private static final Pattern DELETE = Pattern.compile(
        "(?:elimina|borra|eliminar|borrar)\\s+(?:el\\s+|la\\s+)?(?:nodo\\s+|tarea\\s+)?(.+)"
    );

    // RENAME_NODE: "cambia el nombre de INICIO a RECEPCION"
    private static final Pattern RENAME_FROM_TO = Pattern.compile(
        "cambia(?:\\s+el)?\\s+nombre\\s+de\\s+(.+?)\\s+a\\s+(.+)"
    );
    // RENAME_NODE (forma corta): "renombra INICIO a RECEPCION"
    private static final Pattern RENAME_SHORT = Pattern.compile(
        "renombra\\s+(.+?)\\s+a\\s+(.+)"
    );

    // SET_NODE_DEPARTMENT: "asigna REVISION al departamento TECNICO"
    private static final Pattern SET_DEPT = Pattern.compile(
        "asigna\\s+(.+?)\\s+al?\\s+departamento\\s+(.+)"
    );
    // SET_NODE_DEPARTMENT (invertido): "asigna el departamento TECNICO al nodo REVISION"
    private static final Pattern SET_DEPT_INV = Pattern.compile(
        "asigna\\s+(?:el\\s+)?departamento\\s+(.+?)\\s+al?\\s+(?:nodo\\s+)?(.+)"
    );

    // SET_NODE_SLA: "define el sla de APROBACION en 24 horas"
    private static final Pattern SET_SLA = Pattern.compile(
        "(?:define|establece|cambia|pon)\\s+(?:el\\s+)?sla\\s+(?:de\\s+(.+?)\\s+)?(?:en|a)\\s+(\\d+)\\s+horas?"
    );
    // SET_NODE_SLA (forma corta): "48 horas para REVISION TECNICA"
    private static final Pattern SET_SLA_SHORT = Pattern.compile(
        "(\\d+)\\s+horas?\\s+(?:para|de|al?\\s+nodo)\\s+(.+)"
    );

    // CREATE_PROCESS: "crea un proceso llamado NOMBRE"
    private static final Pattern CREATE_PROC = Pattern.compile(
        "crea(?:r)?\\s+(?:un\\s+)?proceso\\s+(?:llamado\\s+|de nombre\\s+)?(.+)"
    );

    // CREATE_DEPARTMENT: "crea el departamento COMERCIAL"
    private static final Pattern CREATE_DEPT = Pattern.compile(
        "crea(?:r)?\\s+(?:el\\s+|un\\s+)?departamento\\s+(?:llamado\\s+)?(.+)"
    );

    // DEACTIVATE_DEPARTMENT: "desactiva el departamento COMERCIAL"
    private static final Pattern DEACTIVATE_DEPT = Pattern.compile(
        "desactiva(?:r)?\\s+(?:el\\s+)?departamento\\s+(.+)"
    );

    // SEARCH_TRAMITE: "busca el tramite TRM-001"
    private static final Pattern SEARCH_TRAMITE = Pattern.compile(
        "busca(?:r)?\\s+(?:el\\s+)?tramite\\s+(.+)"
    );

    // DELETE_TRAMITE: "elimina el tramite TRM-001"
    private static final Pattern DELETE_TRAMITE = Pattern.compile(
        "(?:elimina|borra)(?:r)?\\s+(?:el\\s+)?tramite\\s+(.+)"
    );

    // CREATE_USER: "crea el usuario JUAN PEREZ"
    private static final Pattern CREATE_USER = Pattern.compile(
        "crea(?:r)?\\s+(?:el\\s+|un\\s+|una\\s+)?usuario\\s+(?:llamado\\s+)?(.+)"
    );

    // ASSIGN_USER_DEPARTMENT: "asigna el usuario JUAN al departamento TECNICO"
    private static final Pattern ASSIGN_USER_DEPT = Pattern.compile(
        "asigna\\s+(?:el\\s+|al\\s+)?usuario\\s+(.+?)\\s+al?\\s+departamento\\s+(.+)"
    );

    // ADD_FORM_FIELD: "agrega campo de texto nombre completo"
    private static final Pattern ADD_FIELD = Pattern.compile(
        "agrega(?:r)?\\s+(?:un\\s+|una\\s+)?campo\\s+(?:de\\s+(?:tipo\\s+)?)?" +
        "(texto|area de texto|lista|desplegable|casilla|archivo)?\\s*(?:llamado\\s+)?(.+)"
    );

    // ═══════════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ═══════════════════════════════════════════════════════════════════════════

    public VoiceCommand parseCommand(String text, String processId) {
        // ── 1. Intentar con LLM ──────────────────────────────────────────────────
        if (aiService != null) {
            try {
                VoiceCommand aiResult = callAiService(text, processId);
                if (aiResult != null && aiResult.getAction() != VoiceAction.UNKNOWN) {
                    log.debug("[VoiceAI] Comando interpretado por LLM: {}", aiResult.getAction());
                    return aiResult;
                }
                log.debug("[VoiceAI] LLM retornó UNKNOWN, usando fallback regex");
            } catch (Exception e) {
                log.warn("[VoiceAI] Falló el LLM ({}), usando fallback regex", e.getMessage());
            }
        }

        // ── 2. Fallback: parser regex ─────────────────────────────────────────────
        return parseWithRegex(text, processId);
    }

    /**
     * Llama al AI Service y mapea la respuesta JSON a VoiceCommand.
     * Lanza excepción en caso de JSON inválido o action desconocida (para que el caller haga fallback).
     */
    private VoiceCommand callAiService(String text, String processId) throws Exception {
        String context = (processId != null && !processId.isBlank())
            ? "processId=" + processId
            : "sin contexto de proceso";

        String raw = aiService.interpretCommand(text, context);

        // Limpiar posibles code fences que el LLM añada a pesar del prompt
        String json = raw
            .replaceAll("(?s)^```json\\s*", "")
            .replaceAll("(?s)^```\\s*",      "")
            .replaceAll("(?s)```\\s*$",       "")
            .trim();

        Map<String, Object> data = objectMapper.readValue(json, new TypeReference<>() {});

        String actionStr = (String) data.get("action");
        if (actionStr == null || actionStr.isBlank()) {
            throw new IllegalStateException("JSON sin campo 'action'");
        }

        VoiceAction action;
        try {
            action = VoiceAction.valueOf(actionStr.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("action desconocida: " + actionStr);
        }

        String bpmnType = (String) data.get("bpmnType");

        VoiceCommand cmd = VoiceCommand.builder()
            .action(action)
            .nodeName((String) data.get("nodeName"))
            .sourceNode((String) data.get("sourceNode"))
            .targetNode((String) data.get("targetNode"))
            .newName((String) data.get("newName"))
            .bpmnType(bpmnType)
            .nodeType(bpmnType != null ? bpmnTypeToNodeType(bpmnType) : null)
            .departmentName((String) data.get("departmentName"))
            .userName((String) data.get("userName"))
            .slaHours(data.get("slaHours") != null ? ((Number) data.get("slaHours")).intValue() : null)
            .processName((String) data.get("processName"))
            .tramiteCode((String) data.get("tramiteCode"))
            .fieldName((String) data.get("fieldName"))
            .fieldType((String) data.get("fieldType"))
            .confidence((String) data.getOrDefault("confidence", "HIGH"))
            .message((String) data.get("message"))
            .processId(processId)
            .rawText(text)
            .build();

        return cmd;
    }

    private VoiceCommand parseWithRegex(String text, String processId) {
        String norm = normalize(text);

        VoiceCommand cmd = null;

        // Orden: más específico → más general
        if (cmd == null) cmd = tryRenameNode(norm);          // antes que delete (evita "cambia" → delete)
        if (cmd == null) cmd = tryConnectNodes(norm);
        if (cmd == null) cmd = tryDeleteTramite(norm);       // antes de delete genérico
        if (cmd == null) cmd = tryDeleteNode(norm);
        if (cmd == null) cmd = trySetDepartment(norm);
        if (cmd == null) cmd = trySetSla(norm);
        if (cmd == null) cmd = tryAssignUserDepartment(norm);
        if (cmd == null) cmd = tryAddFormField(norm);
        if (cmd == null) cmd = tryAddNode(norm);             // cubre start, end, gateway, task, nodo
        if (cmd == null) cmd = tryCreateProcess(norm);
        if (cmd == null) cmd = tryCreateDepartment(norm);
        if (cmd == null) cmd = tryDeactivateDepartment(norm);
        if (cmd == null) cmd = tryDeactivateProcess(norm);
        if (cmd == null) cmd = tryPublishProcess(norm);
        if (cmd == null) cmd = trySaveProcess(norm);
        if (cmd == null) cmd = trySearchTramite(norm);
        if (cmd == null) cmd = tryCreateUser(norm);
        if (cmd == null) cmd = tryZoomIn(norm);
        if (cmd == null) cmd = tryZoomOut(norm);
        if (cmd == null) cmd = tryFitScreen(norm);

        if (cmd == null) {
            cmd = VoiceCommand.builder()
                .action(VoiceAction.UNKNOWN)
                .confidence("LOW")
                .message("Comando no reconocido. Prueba con: 'agrega nodo', 'conecta X con Y', 'guarda', 'publica'.")
                .build();
        }

        cmd.setProcessId(processId);
        cmd.setRawText(text);
        return cmd;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — CANVAS BPMN
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand tryAddNode(String norm) {
        if (!ADD_TRIGGER.matcher(norm).find()) return null;

        // Determinar tipo BPMN y acción
        String bpmnType = detectBpmnType(norm);
        VoiceAction action = bpmnTypeToAction(bpmnType);

        // Extraer nombre del elemento
        String name = extractNameAfterType(norm);

        String confidence = (name != null && !name.isBlank()) ? "HIGH" : "MEDIUM";
        String displayName = (name != null && !name.isBlank()) ? capitalize(name) : null;

        String msg = buildAddMessage(action, bpmnType, displayName);

        return VoiceCommand.builder()
            .action(action)
            .bpmnType(bpmnType)
            .nodeType(bpmnTypeToNodeType(bpmnType))
            .nodeName(displayName)
            .confidence(confidence)
            .message(msg)
            .build();
    }

    private VoiceCommand tryConnectNodes(String norm) {
        Matcher m = CONNECT.matcher(norm);
        if (!m.find()) return null;

        String src = capitalize(m.group(1).trim());
        String tgt = capitalize(m.group(2).trim());

        return VoiceCommand.builder()
            .action(VoiceAction.CONNECT_NODES)
            .sourceNode(src)
            .targetNode(tgt)
            .confidence("HIGH")
            .message("Conectar '" + src + "' → '" + tgt + "'")
            .build();
    }

    private VoiceCommand tryDeleteNode(String norm) {
        // Evitar que "elimina el tramite" llegue aquí (ya lo captura tryDeleteTramite)
        if (norm.contains("tramite")) return null;

        Matcher m = DELETE.matcher(norm);
        if (!m.find()) return null;

        String name = capitalize(m.group(1).trim());

        return VoiceCommand.builder()
            .action(VoiceAction.DELETE_NODE)
            .nodeName(name)
            .confidence("HIGH")
            .message("Eliminar nodo '" + name + "' del diagrama")
            .build();
    }

    private VoiceCommand tryRenameNode(String norm) {
        // Forma larga: "cambia el nombre de X a Y"
        Matcher m = RENAME_FROM_TO.matcher(norm);
        if (m.find()) {
            String oldName = capitalize(m.group(1).trim());
            String newName = capitalize(m.group(2).trim());
            return VoiceCommand.builder()
                .action(VoiceAction.RENAME_NODE)
                .nodeName(oldName)
                .newName(newName)
                .confidence("HIGH")
                .message("Renombrar '" + oldName + "' a '" + newName + "'")
                .build();
        }

        // Forma corta: "renombra X a Y"
        m = RENAME_SHORT.matcher(norm);
        if (m.find()) {
            String oldName = capitalize(m.group(1).trim());
            String newName = capitalize(m.group(2).trim());
            return VoiceCommand.builder()
                .action(VoiceAction.RENAME_NODE)
                .nodeName(oldName)
                .newName(newName)
                .confidence("HIGH")
                .message("Renombrar '" + oldName + "' a '" + newName + "'")
                .build();
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — PROPIEDADES DE NODO
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand trySetDepartment(String norm) {
        // "asigna REVISION al departamento TECNICO"
        Matcher m = SET_DEPT.matcher(norm);
        if (m.find()) {
            String node = capitalize(m.group(1).trim());
            String dept = capitalize(m.group(2).trim());
            return VoiceCommand.builder()
                .action(VoiceAction.SET_NODE_DEPARTMENT)
                .nodeName(node)
                .departmentName(dept)
                .confidence("HIGH")
                .message("Asignar nodo '" + node + "' al departamento '" + dept + "'")
                .build();
        }

        // "asigna el departamento TECNICO al nodo REVISION" (orden invertido)
        m = SET_DEPT_INV.matcher(norm);
        if (m.find()) {
            String dept = capitalize(m.group(1).trim());
            String node = capitalize(m.group(2).trim());
            return VoiceCommand.builder()
                .action(VoiceAction.SET_NODE_DEPARTMENT)
                .nodeName(node)
                .departmentName(dept)
                .confidence("HIGH")
                .message("Asignar nodo '" + node + "' al departamento '" + dept + "'")
                .build();
        }

        return null;
    }

    private VoiceCommand trySetSla(String norm) {
        // "define el sla de REVISION en 24 horas"
        Matcher m = SET_SLA.matcher(norm);
        if (m.find()) {
            String node  = m.group(1) != null ? capitalize(m.group(1).trim()) : null;
            int    hours = Integer.parseInt(m.group(2));
            String msg   = node != null
                ? "Establecer SLA de " + hours + " horas para '" + node + "'"
                : "Establecer SLA de " + hours + " horas para el nodo seleccionado";
            return VoiceCommand.builder()
                .action(VoiceAction.SET_NODE_SLA)
                .nodeName(node)
                .slaHours(hours)
                .confidence(node != null ? "HIGH" : "MEDIUM")
                .message(msg)
                .build();
        }

        // "48 horas para REVISION TECNICA"
        m = SET_SLA_SHORT.matcher(norm);
        if (m.find()) {
            int    hours = Integer.parseInt(m.group(1));
            String node  = capitalize(m.group(2).trim());
            return VoiceCommand.builder()
                .action(VoiceAction.SET_NODE_SLA)
                .nodeName(node)
                .slaHours(hours)
                .confidence("HIGH")
                .message("Establecer SLA de " + hours + " horas para '" + node + "'")
                .build();
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — CICLO DE VIDA DEL PROCESO
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand trySaveProcess(String norm) {
        if (!norm.contains("guarda")) return null;
        return VoiceCommand.builder()
            .action(VoiceAction.SAVE_PROCESS)
            .confidence("HIGH")
            .message("Guardar el proceso actual")
            .build();
    }

    private VoiceCommand tryPublishProcess(String norm) {
        if (!norm.contains("publica")) return null;
        return VoiceCommand.builder()
            .action(VoiceAction.PUBLISH_PROCESS)
            .confidence("HIGH")
            .message("Publicar el proceso — quedará disponible para trámites")
            .build();
    }

    private VoiceCommand tryDeactivateProcess(String norm) {
        if (!(norm.contains("desactiva") && norm.contains("proceso"))) return null;
        return VoiceCommand.builder()
            .action(VoiceAction.DEACTIVATE_PROCESS)
            .confidence("HIGH")
            .message("Desactivar el proceso — dejará de aceptar nuevos trámites")
            .build();
    }

    private VoiceCommand tryCreateProcess(String norm) {
        Matcher m = CREATE_PROC.matcher(norm);
        if (!m.find()) return null;

        String name = capitalize(m.group(1).trim());
        return VoiceCommand.builder()
            .action(VoiceAction.CREATE_PROCESS)
            .processName(name)
            .confidence("HIGH")
            .message("Crear nuevo proceso '" + name + "'")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — VISTA DEL CANVAS
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand tryZoomIn(String norm) {
        if (!containsAny(norm, "acerca", "amplia", "zoom in", "acercar", "ampliar")) return null;
        return VoiceCommand.builder()
            .action(VoiceAction.ZOOM_IN)
            .confidence("HIGH")
            .message("Acercar zoom del canvas")
            .build();
    }

    private VoiceCommand tryZoomOut(String norm) {
        if (!containsAny(norm, "aleja", "reduce", "zoom out", "alejar", "reducir")) return null;
        return VoiceCommand.builder()
            .action(VoiceAction.ZOOM_OUT)
            .confidence("HIGH")
            .message("Alejar zoom del canvas")
            .build();
    }

    private VoiceCommand tryFitScreen(String norm) {
        if (!containsAny(norm, "ajusta", "centrar", "centra", "fit", "ver todo", "encuadra", "ajustar")) return null;
        return VoiceCommand.builder()
            .action(VoiceAction.FIT_SCREEN)
            .confidence("HIGH")
            .message("Ajustar diagrama completo a la pantalla")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — DEPARTAMENTOS
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand tryCreateDepartment(String norm) {
        Matcher m = CREATE_DEPT.matcher(norm);
        if (!m.find()) return null;

        String name = capitalize(m.group(1).trim());
        return VoiceCommand.builder()
            .action(VoiceAction.CREATE_DEPARTMENT)
            .departmentName(name)
            .confidence("HIGH")
            .message("Crear departamento '" + name + "'")
            .build();
    }

    private VoiceCommand tryDeactivateDepartment(String norm) {
        Matcher m = DEACTIVATE_DEPT.matcher(norm);
        if (!m.find()) return null;

        String name = capitalize(m.group(1).trim());
        return VoiceCommand.builder()
            .action(VoiceAction.DEACTIVATE_DEPARTMENT)
            .departmentName(name)
            .confidence("HIGH")
            .message("Desactivar departamento '" + name + "'")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — TRÁMITES
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand trySearchTramite(String norm) {
        Matcher m = SEARCH_TRAMITE.matcher(norm);
        if (!m.find()) return null;

        String code = m.group(1).trim().toUpperCase();
        return VoiceCommand.builder()
            .action(VoiceAction.SEARCH_TRAMITE)
            .tramiteCode(code)
            .confidence("HIGH")
            .message("Buscar trámite con código '" + code + "'")
            .build();
    }

    private VoiceCommand tryDeleteTramite(String norm) {
        Matcher m = DELETE_TRAMITE.matcher(norm);
        if (!m.find()) return null;

        String code = m.group(1).trim().toUpperCase();
        return VoiceCommand.builder()
            .action(VoiceAction.DELETE_TRAMITE)
            .tramiteCode(code)
            .confidence("HIGH")
            .message("Eliminar trámite '" + code + "'")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — USUARIOS
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand tryCreateUser(String norm) {
        Matcher m = CREATE_USER.matcher(norm);
        if (!m.find()) return null;

        String name = capitalize(m.group(1).trim());
        return VoiceCommand.builder()
            .action(VoiceAction.CREATE_USER)
            .userName(name)
            .confidence("HIGH")
            .message("Crear usuario '" + name + "'")
            .build();
    }

    private VoiceCommand tryAssignUserDepartment(String norm) {
        Matcher m = ASSIGN_USER_DEPT.matcher(norm);
        if (!m.find()) return null;

        String user = capitalize(m.group(1).trim());
        String dept = capitalize(m.group(2).trim());
        return VoiceCommand.builder()
            .action(VoiceAction.ASSIGN_USER_DEPARTMENT)
            .userName(user)
            .departmentName(dept)
            .confidence("HIGH")
            .message("Asignar usuario '" + user + "' al departamento '" + dept + "'")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGLAS — FORMULARIOS
    // ═══════════════════════════════════════════════════════════════════════════

    private VoiceCommand tryAddFormField(String norm) {
        Matcher m = ADD_FIELD.matcher(norm);
        if (!m.find()) return null;

        String rawType  = m.group(1);
        String rawName  = m.group(2);

        if (rawName == null || rawName.isBlank()) return null;

        String fieldType = mapFieldType(rawType);
        String fieldName = capitalize(rawName.trim());

        return VoiceCommand.builder()
            .action(VoiceAction.ADD_FORM_FIELD)
            .fieldName(fieldName)
            .fieldType(fieldType)
            .confidence("HIGH")
            .message("Agregar campo " + fieldType + " '" + fieldName + "' al formulario del nodo")
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILIDADES PRIVADAS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Normaliza el texto para facilitar el matching:
     *  – minúsculas
     *  – elimina tildes (á→a, é→e, etc.) y ñ→n
     *  – elimina puntuación y signos no alfanuméricos
     *  – colapsa espacios múltiples
     */
    private String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
            .replaceAll("[áàä]", "a")
            .replaceAll("[éèë]", "e")
            .replaceAll("[íìï]", "i")
            .replaceAll("[óòö]", "o")
            .replaceAll("[úùü]", "u")
            .replaceAll("ñ", "n")
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    /**
     * Detecta el tipo BPMN del elemento a partir del texto normalizado.
     * El orden importa: más específico → más general.
     */
    private String detectBpmnType(String norm) {
        if (containsAny(norm, "evento de inicio", "inicio", "start event")) return "bpmn:StartEvent";
        if (containsAny(norm, "evento de fin", "evento final", "fin", "end event")) return "bpmn:EndEvent";
        if (containsAny(norm, "evento intermedio", "intermediate"))            return "bpmn:IntermediateCatchEvent";
        if (containsAny(norm, "tarea de usuario", "tarea usuario", "user task"))  return "bpmn:UserTask";
        if (containsAny(norm, "tarea de servicio", "tarea servicio", "service task")) return "bpmn:ServiceTask";
        if (containsAny(norm, "tarea de envio", "tarea envio", "send task"))   return "bpmn:SendTask";
        if (containsAny(norm, "tarea de recepcion", "receive task"))            return "bpmn:ReceiveTask";
        if (containsAny(norm, "tarea de script", "script task"))                return "bpmn:ScriptTask";
        if (containsAny(norm, "gateway exclusivo", "compuerta exclusiva", "exclusivo")) return "bpmn:ExclusiveGateway";
        if (containsAny(norm, "gateway paralelo", "compuerta paralela", "paralelo"))    return "bpmn:ParallelGateway";
        if (containsAny(norm, "gateway inclusivo", "compuerta inclusiva", "inclusivo")) return "bpmn:InclusiveGateway";
        if (containsAny(norm, "gateway", "compuerta", "decision"))              return "bpmn:ExclusiveGateway";
        if (containsAny(norm, "sub proceso", "subproceso", "subprocess"))       return "bpmn:SubProcess";
        if (norm.contains("tarea"))                                             return "bpmn:UserTask";
        return "bpmn:Task";  // nodo genérico
    }

    /** Mapea tipo BPMN a VoiceAction */
    private VoiceAction bpmnTypeToAction(String bpmnType) {
        return switch (bpmnType) {
            case "bpmn:StartEvent"              -> VoiceAction.ADD_START_EVENT;
            case "bpmn:EndEvent"                -> VoiceAction.ADD_END_EVENT;
            case "bpmn:ExclusiveGateway",
                 "bpmn:ParallelGateway",
                 "bpmn:InclusiveGateway"        -> VoiceAction.ADD_GATEWAY;
            case "bpmn:UserTask",
                 "bpmn:ServiceTask",
                 "bpmn:SendTask",
                 "bpmn:ReceiveTask",
                 "bpmn:ScriptTask"              -> VoiceAction.ADD_TASK;
            default                             -> VoiceAction.ADD_NODE;
        };
    }

    /** Mapea tipo BPMN al NodeType interno del sistema */
    private NodeType bpmnTypeToNodeType(String bpmnType) {
        return switch (bpmnType) {
            case "bpmn:StartEvent"              -> NodeType.START;
            case "bpmn:EndEvent"                -> NodeType.END;
            case "bpmn:ExclusiveGateway",
                 "bpmn:InclusiveGateway"        -> NodeType.EXCLUSIVE_GW;
            case "bpmn:ParallelGateway"         -> NodeType.PARALLEL_GW;
            default                             -> NodeType.USER_TASK;
        };
    }

    /**
     * Extrae el nombre del elemento después de la palabra clave del tipo.
     * Ej: "agrega una tarea de usuario llamada revision tecnica" → "revision tecnica"
     */
    private String extractNameAfterType(String norm) {
        Matcher m = NAME_AFTER_TYPE.matcher(norm);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Fallback: todo lo que hay después del disparador + artículo
        String stripped = norm.replaceAll(
            "^(agrega|anade|agregar|anadir|inserta|insertar|crea|crear)\\s+(?:un|una|el|la)?\\s*", "");
        // Quitar el keyword de tipo si quedó al inicio
        stripped = stripped.replaceAll(
            "^(nodo|tarea|gateway|compuerta|evento|sub\\s*proceso)\\s+", "");
        return stripped.isBlank() ? null : stripped.trim();
    }

    /** Tipo de campo de formulario desde texto del usuario */
    private String mapFieldType(String rawType) {
        if (rawType == null) return "TEXT";
        return switch (rawType.trim()) {
            case "area de texto"             -> "TEXTAREA";
            case "lista", "desplegable"      -> "SELECT";
            case "casilla"                   -> "CHECKBOX";
            case "archivo"                   -> "FILE";
            default                          -> "TEXT";
        };
    }

    private String buildAddMessage(VoiceAction action, String bpmnType, String name) {
        String typeLabel = switch (bpmnType) {
            case "bpmn:StartEvent"       -> "evento de inicio";
            case "bpmn:EndEvent"         -> "evento de fin";
            case "bpmn:UserTask"         -> "tarea de usuario";
            case "bpmn:ServiceTask"      -> "tarea de servicio";
            case "bpmn:SendTask"         -> "tarea de envío";
            case "bpmn:ReceiveTask"      -> "tarea de recepción";
            case "bpmn:ScriptTask"       -> "tarea de script";
            case "bpmn:ExclusiveGateway" -> "gateway exclusivo";
            case "bpmn:ParallelGateway"  -> "gateway paralelo";
            case "bpmn:InclusiveGateway" -> "gateway inclusivo";
            case "bpmn:SubProcess"       -> "sub-proceso";
            default                      -> "nodo";
        };
        return name != null
            ? "Agregar " + typeLabel + " '" + name + "' al diagrama"
            : "Agregar " + typeLabel + " al diagrama";
    }

    /** true si el texto contiene alguna de las palabras clave */
    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /** Capitaliza la primera letra de cada palabra */
    private String capitalize(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }
}
