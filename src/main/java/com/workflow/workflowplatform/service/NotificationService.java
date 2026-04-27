package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.dto.WorkflowEventDTO;
import com.workflow.workflowplatform.model.Node;
import com.workflow.workflowplatform.model.Notification;
import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.model.Tramite;
import com.workflow.workflowplatform.model.enums.TramiteStatus;
import com.workflow.workflowplatform.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── Canales ──────────────────────────────────────────────────────────────
    //
    //  /topic/dept/{departmentId}   → funcionarios del departamento (con persistencia en DB)
    //  /topic/tramite/{tramiteId}   → observadores de un trámite concreto (sin persistencia)
    //  /topic/company/{companyId}   → admin dashboard de la empresa    (sin persistencia)
    //
    // Solo el canal de departamento persiste en MongoDB porque es el único que necesita
    // un contador de no-leídos y la lista de notificaciones en el inbox del funcionario.
    // Los otros dos son actualizaciones de estado en vivo (ephemeral).

    // ─── API para el motor de workflow ────────────────────────────────────────

    /**
     * Llama al finalizar assignTask(): el motor acaba de crear una nueva TaskSubmission.
     * Emite simultáneamente a los 3 canales.
     */
    public void emitTaskAssigned(Tramite tramite, Process process, Node node) {
        WorkflowEventDTO event = buildEvent("TASK_ASSIGNED", tramite, process, node);

        // 1. Departamento receptor — con persistencia (badge de no-leídos en el frontend)
        if (node != null && node.getDepartmentId() != null) {
            String msg = "Nueva tarea: " + labelOf(node) + " · " + tramite.getCode();
            persistDeptNotification(node.getDepartmentId(), tramite.getId(), msg);
            messagingTemplate.convertAndSend("/topic/dept/" + node.getDepartmentId(), event);
        }

        // 2. Observadores del trámite (vista de seguimiento del cliente / funcionario)
        messagingTemplate.convertAndSend("/topic/tramite/" + tramite.getId(), event);

        // 3. Admin dashboard de la empresa
        if (process.getCompanyId() != null) {
            messagingTemplate.convertAndSend("/topic/company/" + process.getCompanyId(), event);
        }

        log.debug("WS emitTaskAssigned tramite={} node={} dept={}",
                tramite.getCode(), labelOf(node), node != null ? node.getDepartmentId() : "—");
    }

    /**
     * Llama al finalizar completeTramite(): el trámite llegó a un nodo END.
     * Emite al canal del trámite y al canal de empresa; no hay departamento receptor.
     */
    public void emitTramiteCompleted(Tramite tramite, Process process) {
        boolean cancelled = TramiteStatus.CANCELLED.equals(tramite.getStatus());
        WorkflowEventDTO event = buildEvent(
                cancelled ? "TRAMITE_CANCELLED" : "TRAMITE_COMPLETED",
                tramite, process, null);

        messagingTemplate.convertAndSend("/topic/tramite/" + tramite.getId(), event);

        if (process.getCompanyId() != null) {
            messagingTemplate.convertAndSend("/topic/company/" + process.getCompanyId(), event);
        }

        log.debug("WS emitTramiteCompleted tramite={} status={}", tramite.getCode(), tramite.getStatus());
    }

    // ─── API directa (para uso explícito fuera del motor) ────────────────────

    /**
     * Envía y persiste una notificación ad-hoc a un departamento.
     * Útil para notificaciones manuales o desde controladores.
     */
    public void notifyDepartment(String departmentId, String tramiteId, String message) {
        persistDeptNotification(departmentId, tramiteId, message);
        messagingTemplate.convertAndSend("/topic/dept/" + departmentId,
                buildRawNotification(departmentId, tramiteId, message));
    }

    // ─── Helpers privados ─────────────────────────────────────────────────────

    private WorkflowEventDTO buildEvent(String type, Tramite tramite, Process process, Node node) {
        return WorkflowEventDTO.builder()
                .type(type)
                .tramiteId(tramite.getId())
                .tramiteCode(tramite.getCode())
                .processId(process.getId())
                .processName(process.getName())
                .nodeId(node != null ? node.getId() : null)
                .nodeName(node != null ? labelOf(node) : null)
                .departmentId(node != null ? node.getDepartmentId() : null)
                .companyId(process.getCompanyId())
                .status(tramite.getStatus() != null ? tramite.getStatus().name() : "ACTIVE")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void persistDeptNotification(String departmentId, String tramiteId, String message) {
        notificationRepository.save(Notification.builder()
                .userId(departmentId)
                .tramiteId(tramiteId)
                .message(message)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Notification buildRawNotification(String userId, String tramiteId, String message) {
        return Notification.builder()
                .userId(userId)
                .tramiteId(tramiteId)
                .message(message)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private String labelOf(Node node) {
        if (node == null) return "—";
        return node.getLabel() != null ? node.getLabel() : node.getId();
    }
}
