package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.model.Edge;
import com.workflow.workflowplatform.model.Node;
import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.model.TaskSubmission;
import com.workflow.workflowplatform.model.Tramite;
import com.workflow.workflowplatform.model.enums.NodeType;
import com.workflow.workflowplatform.model.enums.TramiteStatus;
import com.workflow.workflowplatform.repository.ProcessRepository;
import com.workflow.workflowplatform.repository.TaskSubmissionRepository;
import com.workflow.workflowplatform.repository.TramiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowEngineService {

    private final TramiteRepository tramiteRepository;
    private final ProcessRepository processRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final NotificationService notificationService;

    @Autowired
    private PushNotificationService pushNotificationService;

    // ─── Punto de entrada ────────────────────────────────────────────────────

    public void advance(String tramiteId, String completedNodeId, Map<String, Object> formData) {
        Tramite tramite = tramiteRepository.findById(tramiteId)
                .orElseThrow(() -> new RuntimeException("Trámite no encontrado: " + tramiteId));

        Process process = processRepository.findById(tramite.getProcessId())
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + tramite.getProcessId()));

        // Si hay ramas paralelas pendientes, manejar el join en vez del flujo normal
        if (tramite.getParallelPending() != null && !tramite.getParallelPending().isEmpty()) {
            tramite.getParallelPending().remove(completedNodeId);
            if (tramite.getParallelPending().isEmpty()) {
                // Todas las ramas completadas: avanzar al nodo join (outgoing del branch completado)
                Edge joinEdge = findOutgoingEdge(process, completedNodeId);
                String joinNodeId = (joinEdge != null) ? joinEdge.getTarget() : tramite.getCurrentNodeId();
                tramite.setCurrentNodeId(joinNodeId);
                tramite.setUpdatedAt(LocalDateTime.now());
                tramiteRepository.save(tramite);
                handleNextNode(tramite, process, joinNodeId, formData);
            } else {
                tramite.setUpdatedAt(LocalDateTime.now());
                tramiteRepository.save(tramite);
            }
            return;
        }

        Node currentNode = findNodeById(process, tramite.getCurrentNodeId());
        if (currentNode == null) {
            throw new RuntimeException("Nodo actual no encontrado: " + tramite.getCurrentNodeId());
        }

        switch (currentNode.getType()) {
            case START:
            case USER_TASK:
                moveToSequence(tramite, process, formData);
                break;
            case EXCLUSIVE_GW:
                evaluateGateway(tramite, process, formData);
                break;
            case PARALLEL_GW:
                forkParallel(tramite, process, formData);
                break;
            case LOOP_GW:
                evaluateLoop(tramite, process, formData);
                break;
            case END:
                completeTramite(tramite);
                break;
            default:
                throw new RuntimeException("Tipo de nodo no soportado: " + currentNode.getType());
        }
    }

    // ─── Nodo siguiente secuencial ────────────────────────────────────────────

    private void moveToSequence(Tramite tramite, Process process, Map<String, Object> formData) {
        Edge nextEdge = findOutgoingEdge(process, tramite.getCurrentNodeId());
        if (nextEdge == null) {
            throw new RuntimeException("No hay edge saliente desde: " + tramite.getCurrentNodeId());
        }

        String targetId = nextEdge.getTarget();
        tramite.setCurrentNodeId(targetId);
        tramite.setUpdatedAt(LocalDateTime.now());
        tramiteRepository.save(tramite);

        Node siguienteNodo = findNodeById(process, targetId);
        try {
            pushNotificationService.sendToTramite(
                    tramite.getCode(),
                    "Tu trámite avanzó ✓",
                    "Tu solicitud " + tramite.getCode() +
                    " pasó a: " + (siguienteNodo != null ? siguienteNodo.getLabel() : targetId)
            );
        } catch (Exception e) {
            log.error("Error enviando push notification: " + e.getMessage());
            // No fallar el flujo si el push falla
        }

        // Despachar al nodo destino (puede ser otro gateway, END, o tarea humana)
        handleNextNode(tramite, process, targetId, formData);
    }

    // ─── Gateway exclusivo ────────────────────────────────────────────────────

    private void evaluateGateway(Tramite tramite, Process process, Map<String, Object> formData) {
        List<Edge> outgoing = findOutgoingEdges(process, tramite.getCurrentNodeId());

        // 1. Evaluar primero las edges con condición específica (no vacía)
        for (Edge edge : outgoing) {
            String cond = edge.getCondition();
            if (cond != null && !cond.trim().isEmpty()) {
                if (evaluateCondition(cond, formData)) {
                    String targetId = edge.getTarget();
                    tramite.setCurrentNodeId(targetId);
                    tramite.setUpdatedAt(LocalDateTime.now());
                    tramiteRepository.save(tramite);
                    handleNextNode(tramite, process, targetId, formData);
                    return;
                }
            }
        }

        // 2. Si ninguna condición específica coincidió, usar la edge por defecto (condición vacía)
        for (Edge edge : outgoing) {
            String cond = edge.getCondition();
            if (cond == null || cond.trim().isEmpty()) {
                String targetId = edge.getTarget();
                tramite.setCurrentNodeId(targetId);
                tramite.setUpdatedAt(LocalDateTime.now());
                tramiteRepository.save(tramite);
                handleNextNode(tramite, process, targetId, formData);
                return;
            }
        }

        throw new RuntimeException("Ninguna condición del gateway fue verdadera para nodo: "
                + tramite.getCurrentNodeId());
    }

    // ─── Gateway paralelo ─────────────────────────────────────────────────────

    private void forkParallel(Tramite tramite, Process process, Map<String, Object> formData) {
        List<Edge> outgoing = findOutgoingEdges(process, tramite.getCurrentNodeId());
        if (outgoing.isEmpty()) {
            throw new RuntimeException("No hay branches paralelos desde: " + tramite.getCurrentNodeId());
        }

        List<String> parallelNodes = new ArrayList<>();
        for (Edge edge : outgoing) {
            parallelNodes.add(edge.getTarget());
            assignTask(tramite, process, edge.getTarget());
        }

        tramite.setParallelPending(parallelNodes);
        tramite.setUpdatedAt(LocalDateTime.now());
        tramiteRepository.save(tramite);
    }

    // ─── Gateway de loop ──────────────────────────────────────────────────────

    private void evaluateLoop(Tramite tramite, Process process, Map<String, Object> formData) {
        List<Edge> outgoing = findOutgoingEdges(process, tramite.getCurrentNodeId());

        Edge backEdge = null;
        Edge forwardEdge = null;

        for (Edge edge : outgoing) {
            if (edge.getLabel() != null && edge.getLabel().contains("loop")) {
                backEdge = edge;
            } else {
                forwardEdge = edge;
            }
        }

        String targetId;
        if (evaluateCondition(backEdge != null ? backEdge.getCondition() : null, formData)) {
            if (backEdge == null) throw new RuntimeException("No hay back edge en loop");
            targetId = backEdge.getTarget();
        } else {
            if (forwardEdge == null) throw new RuntimeException("No hay forward edge en loop");
            targetId = forwardEdge.getTarget();
        }

        tramite.setCurrentNodeId(targetId);
        tramite.setUpdatedAt(LocalDateTime.now());
        tramiteRepository.save(tramite);

        handleNextNode(tramite, process, targetId, formData);
    }

    // ─── Despacho al nodo destino ─────────────────────────────────────────────
    // Gateways y END se ejecutan automáticamente.
    // USER_TASK crea una tarea humana y espera.

    private void handleNextNode(Tramite tramite, Process process, String nodeId,
                                Map<String, Object> formData) {
        Node node = findNodeById(process, nodeId);
        if (node == null) return;

        switch (node.getType()) {
            case EXCLUSIVE_GW:
                evaluateGateway(tramite, process, formData);
                break;
            case PARALLEL_GW:
                forkParallel(tramite, process, formData);
                break;
            case LOOP_GW:
                evaluateLoop(tramite, process, formData);
                break;
            case END:
                completeTramite(tramite);
                break;
            default:
                // USER_TASK u otro nodo humano: crear tarea y esperar
                assignTask(tramite, process, nodeId);
        }
    }

    // ─── Crear TaskSubmission ─────────────────────────────────────────────────

    public void assignTask(Tramite tramite, Process process, String nodeId) {
        Node node = findNodeById(process, nodeId);

        TaskSubmission task = TaskSubmission.builder()
                .tramiteId(tramite.getId())
                .nodeId(nodeId)
                .processId(tramite.getProcessId())
                .userId(tramite.getCreatedBy())
                .departmentId(node != null ? node.getDepartmentId() : null)
                .startedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        taskSubmissionRepository.save(task);

        notificationService.emitTaskAssigned(tramite, process, node);
    }

    // ─── Completar trámite ────────────────────────────────────────────────────

    private void completeTramite(Tramite tramite) {
        Process process = processRepository.findById(tramite.getProcessId())
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado"));

        Node endNode = findNodeById(process, tramite.getCurrentNodeId());
        boolean isRejection = endNode != null && endNode.getLabel() != null
                && endNode.getLabel().toLowerCase().contains("rechaz");

        tramite.setStatus(isRejection ? TramiteStatus.CANCELLED : TramiteStatus.COMPLETED);
        tramite.setCompletedDate(LocalDateTime.now());
        tramite.setUpdatedAt(LocalDateTime.now());
        tramiteRepository.save(tramite);

        try {
            pushNotificationService.sendToTramite(
                    tramite.getCode(),
                    "¡Trámite completado! 🎉",
                    "Tu solicitud " + tramite.getCode() +
                    " fue aprobada y completada exitosamente."
            );
        } catch (Exception e) {
            log.error("Error enviando push de completado: " + e.getMessage());
        }

        process.setActiveTramites(Math.max(0, process.getActiveTramites() - 1));
        processRepository.save(process);

        notificationService.emitTramiteCompleted(tramite, process);
    }

    // ─── Evaluación de condiciones ────────────────────────────────────────────

    private boolean evaluateCondition(String condition, Map<String, Object> formData) {
        if (condition == null || condition.isEmpty()) return true;
        if (formData == null || formData.isEmpty()) return false;

        // Formato: "campo == valor"
        if (condition.contains("==")) {
            String[] parts = condition.split("==", 2);
            String field = parts[0].trim();
            String expected = parts[1].trim();
            Object actual = getFieldCaseInsensitive(formData, field);
            return expected.equalsIgnoreCase(String.valueOf(actual));
        }

        // Formato: "campo:valor"
        if (condition.contains(":")) {
            String[] parts = condition.split(":", 2);
            String field = parts[0].trim();
            String expected = parts[1].trim();
            Object actual = getFieldCaseInsensitive(formData, field);
            return expected.equalsIgnoreCase(String.valueOf(actual));
        }

        return false;
    }

    private Object getFieldCaseInsensitive(Map<String, Object> formData, String field) {
        if (formData.containsKey(field)) return formData.get(field);
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(field)) return entry.getValue();
        }
        return null;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Node findNodeById(Process process, String nodeId) {
        if (process.getNodes() == null) return null;
        return process.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private Edge findOutgoingEdge(Process process, String nodeId) {
        if (process.getEdges() == null) return null;
        return process.getEdges().stream()
                .filter(e -> e.getSource().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private List<Edge> findOutgoingEdges(Process process, String nodeId) {
        if (process.getEdges() == null) return new ArrayList<>();
        List<Edge> result = new ArrayList<>();
        process.getEdges().stream()
                .filter(e -> e.getSource().equals(nodeId))
                .forEach(result::add);
        return result;
    }
}
