package com.workflow.workflowplatform.dto;

import com.workflow.workflowplatform.model.enums.NodeType;
import com.workflow.workflowplatform.model.enums.VoiceAction;
import lombok.Builder;
import lombok.Data;

/**
 * Resultado del parsing de un comando de voz.
 * El frontend usa action + campos opcionales para ejecutar la operación.
 */
@Data
@Builder
public class VoiceCommand {

    private VoiceAction action;

    // ── BPMN / nodos ─────────────────────────────────────────────────────────
    private String   nodeName;       // nombre del nodo afectado
    private String   sourceNode;     // origen en CONNECT_NODES
    private String   targetNode;     // destino en CONNECT_NODES
    private String   newName;        // nuevo nombre en RENAME_NODE
    private String   bpmnType;       // tipo bpmn: bpmn:UserTask, bpmn:StartEvent…
    private NodeType nodeType;       // tipo interno: START, END, USER_TASK…

    // ── Propiedades ──────────────────────────────────────────────────────────
    private String  departmentName;  // nombre del departamento
    private String  userName;        // nombre del usuario
    private Integer slaHours;        // horas de SLA
    private String  processName;     // nombre del proceso a crear
    private String  tramiteCode;     // código del trámite

    // ── Formulario ───────────────────────────────────────────────────────────
    private String fieldName;        // nombre del campo a agregar
    private String fieldType;        // TEXT | TEXTAREA | SELECT | CHECKBOX | FILE

    // ── Meta ─────────────────────────────────────────────────────────────────
    private String processId;        // processId recibido del request
    private String confidence;       // HIGH | MEDIUM | LOW
    private String rawText;          // texto original sin normalizar
    private String message;          // descripción legible para el usuario
}
