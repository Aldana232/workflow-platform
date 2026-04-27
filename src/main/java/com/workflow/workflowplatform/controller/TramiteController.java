package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.CreateTramiteRequest;
import com.workflow.workflowplatform.dto.DeptStatsDTO;
import com.workflow.workflowplatform.dto.TaskDetailDTO;
import com.workflow.workflowplatform.dto.TramiteResponseDTO;
import com.workflow.workflowplatform.model.FormSchema;
import com.workflow.workflowplatform.model.TaskSubmission;
import com.workflow.workflowplatform.model.Tramite;
import com.workflow.workflowplatform.service.TramiteService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tramites")
@RequiredArgsConstructor
public class TramiteController {

    private final TramiteService tramiteService;

    /** Crea un nuevo trámite. Solo FUNCIONARIO o ADMIN. */
    @PostMapping
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN')")
    public ResponseEntity<Tramite> createTramite(
            @RequestBody CreateTramiteRequest request,
            Authentication authentication) {
        try {
            Tramite tramite = tramiteService.createTramite(
                    request.getProcessId(), request.getClienteInfo(), authentication);
            return ResponseEntity.status(HttpStatus.CREATED).body(tramite);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Consulta pública por código (ej: TRAM-2026-0001). */
    @GetMapping("/code/{code}")
    public ResponseEntity<Tramite> getTramiteByCode(@PathVariable String code) {
        try {
            return ResponseEntity.ok(tramiteService.getTramiteByCode(code));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Tareas pendientes del funcionario autenticado.
     * Combina:
     *  1. Trámites ACTIVOS creados por el usuario (siempre visibles).
     *  2. TaskSubmissions del departamento, si existen (sin duplicados).
     */
    @GetMapping("/my-tasks")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN','SUPERADMIN')")
    public ResponseEntity<List<TaskDetailDTO>> getMyTasks(
            @RequestParam(required = false) String departmentId,
            Authentication authentication) {
        try {
            // 1. Trámites ACTIVOS y COMPLETADOS creados por el propio usuario (Carlos)
            List<TaskDetailDTO> result =
                    new ArrayList<>(tramiteService.getActiveTramitesAsTasksForUser(authentication));

            String deptId = (departmentId != null && !departmentId.isEmpty())
                    ? departmentId
                    : tramiteService.getDepartmentIdFromAuth(authentication);

            Set<String> seen = new HashSet<>();
            result.forEach(dto -> seen.add(dto.getTramiteId()));

            // 2. TaskSubmissions PENDIENTES del departamento (Mateo/Laura ven tareas activas)
            List<TaskSubmission> pending = (deptId != null && !deptId.isEmpty())
                    ? tramiteService.getTramitesByDepartment(deptId)
                    : tramiteService.getAllPendingTasks();

            tramiteService.getEnrichedTasks(pending).stream()
                    .filter(dto -> seen.add(dto.getTramiteId()))
                    .forEach(result::add);

            // 3. TaskSubmissions COMPLETADAS del departamento (historial read-only de Mateo/Laura)
            if (deptId != null && !deptId.isEmpty()) {
                List<TaskSubmission> completed =
                        tramiteService.getCompletedTasksByDepartment(deptId);
                tramiteService.getEnrichedTasks(completed).stream()
                        .filter(dto -> seen.add(dto.getTramiteId()))
                        .forEach(result::add);
            }

            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Estadísticas reales del departamento del usuario autenticado. */
    @GetMapping("/my-stats")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN','SUPERADMIN')")
    public ResponseEntity<DeptStatsDTO> getMyStats(
            @RequestParam(required = false) String departmentId,
            Authentication authentication) {
        try {
            return ResponseEntity.ok(tramiteService.getDeptStats(authentication, departmentId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Indica si el usuario autenticado puede iniciar nuevos trámites.
     * Devuelve true si su departamento es el primer nodo USER_TASK
     * de al menos un proceso ACTIVO.
     */
    @GetMapping("/can-initiate")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN')")
    public ResponseEntity<Boolean> canInitiate(Authentication authentication) {
        return ResponseEntity.ok(tramiteService.canUserInitiate(authentication));
    }

    /** Detalle de un trámite por ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN','SUPERADMIN')")
    public ResponseEntity<Tramite> getTramiteById(@PathVariable String id) {
        try {
            return ResponseEntity.ok(tramiteService.getTramiteById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Trámites activos de un proceso (solo ADMIN/SUPERADMIN). */
    @GetMapping("/by-process/{processId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<List<TramiteResponseDTO>> getActiveTramitesByProcess(
            @PathVariable String processId) {
        return ResponseEntity.ok(tramiteService.getActiveTramitesByProcess(processId));
    }

    /** Todos los trámites de un proceso, incluyendo COMPLETED (admin). */
    @GetMapping("/by-process/{processId}/all")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<List<TramiteResponseDTO>> getAllTramitesByProcess(
            @PathVariable String processId) {
        return ResponseEntity.ok(tramiteService.getAllTramitesByProcess(processId));
    }

    /** Datos enviados en un nodo específico — vista read-only para el funcionario. */
    @GetMapping("/{tramiteId}/submission/{nodeId}")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN','SUPERADMIN')")
    public ResponseEntity<TaskSubmission> getSubmission(
            @PathVariable String tramiteId,
            @PathVariable String nodeId) {
        try {
            return ResponseEntity.ok(tramiteService.getSubmissionForNode(tramiteId, nodeId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Formulario del nodo actual de un trámite. */
    @GetMapping("/{tramiteId}/current-form")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','FUNCIONARIO')")
    public ResponseEntity<FormSchema> getCurrentForm(@PathVariable String tramiteId) {
        try {
            return ResponseEntity.ok(tramiteService.getCurrentForm(tramiteId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Formulario de un nodo específico de un trámite (usado en ramas paralelas). */
    @GetMapping("/{tramiteId}/form/{nodeId}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN','FUNCIONARIO')")
    public ResponseEntity<FormSchema> getFormForNode(
            @PathVariable String tramiteId,
            @PathVariable String nodeId) {
        try {
            return ResponseEntity.ok(tramiteService.getFormForNode(tramiteId, nodeId));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Completa la tarea actual y avanza el flujo. */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('FUNCIONARIO','ADMIN')")
    public ResponseEntity<Void> submitTask(
            @PathVariable String id,
            @RequestBody TaskSubmitRequest request) {
        try {
            tramiteService.submitTask(
                    id, request.getNodeId(), request.getFormData(), request.getObservations());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }       
    }

    /** Elimina un trámite y sus submissions (solo SUPERADMIN/ADMIN). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','ADMIN')")
    public ResponseEntity<Void> deleteTramite(@PathVariable String id) {
        try {
            tramiteService.deleteTramite(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class TaskSubmitRequest {
        private String nodeId;
        private Map<String, Object> formData;
        private String observations;
    }
}
