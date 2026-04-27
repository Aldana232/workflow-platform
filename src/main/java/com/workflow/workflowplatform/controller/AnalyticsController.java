package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.dto.ActiveTramiteDTO;
import com.workflow.workflowplatform.dto.AnalyticsSummaryDTO;
import com.workflow.workflowplatform.dto.ApiResponseDTO;
import com.workflow.workflowplatform.dto.NodeAnalyticsDTO;
import com.workflow.workflowplatform.dto.BottleneckDTO;
import com.workflow.workflowplatform.dto.DepartmentPerformanceDTO;
import com.workflow.workflowplatform.dto.ProcessCompletionDTO;
import com.workflow.workflowplatform.dto.TramitesByDayDTO;
import com.workflow.workflowplatform.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller para endpoints de Analítica del sistema de Workflow
 * Proporciona métricas, reportes y análisis de procesos y trámites
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'FUNCIONARIO')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Endpoint A: GET /api/analytics/summary/{companyId}
     * Obtiene el resumen general de analíticas para una compañía
     *
     * @param companyId ID de la compañía
     * @return ResponseEntity con AnalyticsSummaryDTO
     *
     * Respuesta exitosa (200):
     * {
     *   "success": true,
     *   "message": "Resumen de analíticas obtenido exitosamente",
     *   "data": {
     *     "totalActiveTramites": 15,
     *     "totalCompletedThisMonth": 42,
     *     "avgDurationMinutes": 127.45,
     *     "totalActiveProcesses": 8
     *   },
     *   "timestamp": "2026-04-20T10:30:45"
     * }
     */
    @GetMapping("/summary/{companyId}")
    public ResponseEntity<ApiResponseDTO<AnalyticsSummaryDTO>> getSummary(
            @PathVariable String companyId) {
        
        log.info("Solicitud: GET /api/analytics/summary/{}", companyId);

        try {
            AnalyticsSummaryDTO summary = analyticsService.getSummary(companyId);

            log.info("Resumen de analíticas obtenido exitosamente para compañía: {}", companyId);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(summary, "Resumen de analíticas obtenido exitosamente")
            );

        } catch (RuntimeException e) {
            log.error("Error runtime al obtener resumen: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener analíticas", e.getMessage())
            );

        } catch (Exception e) {
            log.error("Error inesperado al obtener resumen", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }

    /**
     * Endpoint B: GET /api/analytics/nodes/{processId}
     * Obtiene analíticas detalladas por nodo para un proceso específico
     *
     * @param processId ID del proceso
     * @return ResponseEntity con lista de NodeAnalyticsDTO
     *
     * Respuesta exitosa (200):
     * {
     *   "success": true,
     *   "message": "Analíticas de nodos obtenidas exitosamente",
     *   "data": [
     *     {
     *       "nodeId": "node-001",
     *       "nodeName": "Validación de Documentos",
     *       "avgDurationMinutes": 45.5,
     *       "totalTasks": 156
     *     },
     *     {
     *       "nodeId": "node-002",
     *       "nodeName": "Revisión Gerencial",
     *       "avgDurationMinutes": 120.3,
     *       "totalTasks": 98
     *     },
     *     {
     *       "nodeId": "node-003",
     *       "nodeName": "Aprobación Final",
     *       "avgDurationMinutes": 30.2,
     *       "totalTasks": 156
     *     }
     *   ],
     *   "timestamp": "2026-04-20T10:30:45"
     * }
     */
    @GetMapping("/nodes/{processId}")
    public ResponseEntity<ApiResponseDTO<List<NodeAnalyticsDTO>>> getNodeAnalytics(
            @PathVariable String processId) {
        
        log.info("Solicitud: GET /api/analytics/nodes/{}", processId);

        try {
            if (processId == null || processId.trim().isEmpty()) {
                log.warn("ProcessId vacío en request");
                return ResponseEntity.badRequest().body(
                        ApiResponseDTO.error("ID de proceso requerido", "INVALID_PROCESS_ID")
                );
            }

            List<NodeAnalyticsDTO> nodeAnalytics = analyticsService.getNodeAnalytics(processId);

            log.info("Analíticas de nodos obtenidas: {} nodos para proceso: {}", 
                    nodeAnalytics.size(), processId);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(nodeAnalytics, "Analíticas de nodos obtenidas exitosamente")
            );

        } catch (RuntimeException e) {
            log.error("Error runtime al obtener analíticas de nodos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener analíticas de nodos", e.getMessage())
            );

        } catch (Exception e) {
            log.error("Error inesperado al obtener analíticas de nodos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }

    /**
     * Endpoint C: GET /api/analytics/active-tramites/{companyId}
     * Obtiene los trámites activos de una compañía con detalles de tiempo de espera
     *
     * @param companyId ID de la compañía
     * @return ResponseEntity con lista de ActiveTramiteDTO
     *
     * Respuesta exitosa (200):
     * {
     *   "success": true,
     *   "message": "Trámites activos obtenidos exitosamente",
     *   "data": [
     *     {
     *       "code": "TRAM-2026-0001",
     *       "processName": "Solicitud de Licencia",
     *       "currentNode": "Validación de Documentos",
     *       "departmentName": "Recursos Humanos",
     *       "waitingMinutes": 245
     *     },
     *     {
     *       "code": "TRAM-2026-0002",
     *       "processName": "Cambio de Departamento",
     *       "currentNode": "Aprobación Gerencial",
     *       "departmentName": "Administración",
     *       "waitingMinutes": 1203
     *     },
     *     {
     *       "code": "TRAM-2026-0003",
     *       "processName": "Solicitud de Licencia",
     *       "currentNode": "Revisión Gerencial",
     *       "departmentName": "Recursos Humanos",
     *       "waitingMinutes": 567
     *     }
     *   ],
     *   "timestamp": "2026-04-20T10:30:45"
     * }
     */
    @GetMapping("/active-tramites/{companyId}")
    public ResponseEntity<ApiResponseDTO<List<ActiveTramiteDTO>>> getActiveTramites(
            @PathVariable String companyId) {
        
        log.info("Solicitud: GET /api/analytics/active-tramites/{}", companyId);

        try {
            List<ActiveTramiteDTO> activeTramites = analyticsService.getActiveTramites(companyId);

            log.info("Trámites activos obtenidos: {} trámites para compañía: {}", 
                    activeTramites.size(), companyId);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(activeTramites, "Trámites activos obtenidos exitosamente")
            );

        } catch (RuntimeException e) {
            log.error("Error runtime al obtener trámites activos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener trámites activos", e.getMessage())
            );

        } catch (Exception e) {
            log.error("Error inesperado al obtener trámites activos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }

    /**
     * Endpoint D: GET /api/analytics/bottlenecks/{companyId}
     * Nodos donde el tiempo promedio de ejecución supera el SLA definido.
     * Ordenados de mayor a menor exceso (en minutos).
     */
    @GetMapping("/bottlenecks/{companyId}")
    public ResponseEntity<ApiResponseDTO<List<BottleneckDTO>>> getBottlenecks(
            @PathVariable String companyId) {

        log.info("Solicitud: GET /api/analytics/bottlenecks/{}", companyId);
        try {
            List<BottleneckDTO> bottlenecks = analyticsService.getBottlenecks(companyId);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(bottlenecks, "Cuellos de botella obtenidos exitosamente")
            );
        } catch (RuntimeException e) {
            log.error("Error al obtener cuellos de botella: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener cuellos de botella", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error inesperado al obtener cuellos de botella", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }

    /**
     * Endpoint E: GET /api/analytics/tramites-by-day/{companyId}?days=30
     * Cantidad de trámites creados por día en los últimos N días.
     * El parámetro days es opcional y por defecto es 30.
     */
    @GetMapping("/tramites-by-day/{companyId}")
    public ResponseEntity<ApiResponseDTO<List<TramitesByDayDTO>>> getTramitesByDay(
            @PathVariable String companyId,
            @RequestParam(defaultValue = "30") int days) {

        log.info("Solicitud: GET /api/analytics/tramites-by-day/{} días={}", companyId, days);
        try {
            List<TramitesByDayDTO> data = analyticsService.getTramitesByDay(companyId, days);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(data, "Trámites por día obtenidos exitosamente")
            );
        } catch (RuntimeException e) {
            log.error("Error al obtener trámites por día: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener trámites por día", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error inesperado al obtener trámites por día", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }

    /**
     * Endpoint F: GET /api/analytics/department-performance/{companyId}
     * Rendimiento por departamento: total de tareas completadas, tiempo promedio
     * y porcentaje de cumplimiento de SLA.
     */
    @GetMapping("/department-performance/{companyId}")
    public ResponseEntity<ApiResponseDTO<List<DepartmentPerformanceDTO>>> getDepartmentPerformance(
            @PathVariable String companyId) {

        log.info("Solicitud: GET /api/analytics/department-performance/{}", companyId);
        try {
            List<DepartmentPerformanceDTO> data = analyticsService.getDepartmentPerformance(companyId);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(data, "Rendimiento por departamento obtenido exitosamente")
            );
        } catch (RuntimeException e) {
            log.error("Error al obtener rendimiento por departamento: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener rendimiento por departamento", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error inesperado al obtener rendimiento por departamento", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }

    /**
     * Endpoint G: GET /api/analytics/process-completion/{companyId}
     * Distribución de trámites ACTIVOS por nodo dentro de cada proceso.
     * Útil para detectar dónde se acumulan los trámites en curso.
     */
    @GetMapping("/process-completion/{companyId}")
    public ResponseEntity<ApiResponseDTO<List<ProcessCompletionDTO>>> getProcessCompletion(
            @PathVariable String companyId) {

        log.info("Solicitud: GET /api/analytics/process-completion/{}", companyId);
        try {
            List<ProcessCompletionDTO> data = analyticsService.getProcessCompletion(companyId);
            return ResponseEntity.ok(
                    ApiResponseDTO.success(data, "Distribución de procesos obtenida exitosamente")
            );
        } catch (RuntimeException e) {
            log.error("Error al obtener distribución de procesos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error al obtener distribución de procesos", e.getMessage())
            );
        } catch (Exception e) {
            log.error("Error inesperado al obtener distribución de procesos", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ApiResponseDTO.error("Error interno del servidor", "INTERNAL_SERVER_ERROR")
            );
        }
    }
}
