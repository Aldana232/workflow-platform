package com.workflow.workflowplatform.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Payload canónico que se emite por WebSocket en cada evento del motor de workflow.
 * Todos los canales (dept, tramite, company) reciben este mismo objeto para que el
 * cliente Angular pueda manejar un único tipo de mensaje sin lógica de deserialización distinta.
 */
@Data
@Builder
public class WorkflowEventDTO {

    /**
     * Tipo de evento. Valores posibles:
     *   TASK_ASSIGNED      — el motor asignó una nueva tarea humana a un departamento
     *   TRAMITE_COMPLETED  — el trámite llegó a un nodo END exitoso
     *   TRAMITE_CANCELLED  — el trámite llegó a un nodo END de rechazo
     */
    private String type;

    private String tramiteId;
    private String tramiteCode;

    private String processId;
    private String processName;

    private String nodeId;
    private String nodeName;

    /** Departamento que recibe la tarea. Null en eventos de cierre. */
    private String departmentId;

    /** CompanyId del proceso; permite que el admin filtre por empresa en /topic/company/{id}. */
    private String companyId;

    /** Estado actual del trámite en el momento del evento. */
    private String status;

    private LocalDateTime timestamp;
}
