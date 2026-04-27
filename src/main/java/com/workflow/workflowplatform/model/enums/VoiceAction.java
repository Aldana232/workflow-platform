package com.workflow.workflowplatform.model.enums;

/**
 * Acciones que el agente de voz puede ejecutar sobre la plataforma.
 * Cada valor mapea a una operación real disponible en el sistema.
 */
public enum VoiceAction {

    // ── Canvas BPMN ───────────────────────────────────────────────────────────
    ADD_NODE,            // agrega nodo genérico
    ADD_TASK,            // agrega tarea (UserTask, ServiceTask, etc.)
    ADD_START_EVENT,     // agrega evento de inicio
    ADD_END_EVENT,       // agrega evento de fin
    ADD_GATEWAY,         // agrega gateway (exclusivo, paralelo, inclusivo)
    CONNECT_NODES,       // conecta nodoA con nodoB
    DELETE_NODE,         // elimina nodo por nombre
    RENAME_NODE,         // cambia nombre de nodo

    // ── Propiedades de nodo ───────────────────────────────────────────────────
    SET_NODE_DEPARTMENT, // asigna departamento a un nodo
    SET_NODE_SLA,        // define horas de SLA para un nodo

    // ── Ciclo de vida del proceso ─────────────────────────────────────────────
    SAVE_PROCESS,        // guarda el proceso actual
    PUBLISH_PROCESS,     // publica el proceso
    DEACTIVATE_PROCESS,  // desactiva el proceso
    CREATE_PROCESS,      // crea un proceso nuevo con nombre

    // ── Vista del canvas ──────────────────────────────────────────────────────
    ZOOM_IN,
    ZOOM_OUT,
    FIT_SCREEN,

    // ── Departamentos ─────────────────────────────────────────────────────────
    CREATE_DEPARTMENT,
    DEACTIVATE_DEPARTMENT,

    // ── Trámites ─────────────────────────────────────────────────────────────
    SEARCH_TRAMITE,
    DELETE_TRAMITE,

    // ── Usuarios ─────────────────────────────────────────────────────────────
    CREATE_USER,
    ASSIGN_USER_DEPARTMENT,

    // ── Formularios del nodo ──────────────────────────────────────────────────
    ADD_FORM_FIELD,

    UNKNOWN
}
