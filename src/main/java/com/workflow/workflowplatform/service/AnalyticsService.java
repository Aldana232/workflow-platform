package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.dto.ActiveTramiteDTO;
import com.workflow.workflowplatform.dto.AnalyticsSummaryDTO;
import com.workflow.workflowplatform.dto.NodeAnalyticsDTO;
import com.workflow.workflowplatform.model.Department;
import com.workflow.workflowplatform.model.Node;
import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.model.TaskSubmission;
import com.workflow.workflowplatform.model.Tramite;
import com.workflow.workflowplatform.model.enums.TramiteStatus;
import com.workflow.workflowplatform.repository.DepartmentRepository;
import com.workflow.workflowplatform.repository.ProcessRepository;
import com.workflow.workflowplatform.repository.TaskSubmissionRepository;
import com.workflow.workflowplatform.repository.TramiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.workflow.workflowplatform.dto.BottleneckDTO;
import com.workflow.workflowplatform.dto.DepartmentPerformanceDTO;
import com.workflow.workflowplatform.dto.NodeCountDTO;
import com.workflow.workflowplatform.dto.ProcessCompletionDTO;
import com.workflow.workflowplatform.dto.TramitesByDayDTO;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import java.util.Collections;

/**
 * Servicio de Analíticas para gestión de reportes y métricas de trámites y procesos
 * Utiliza MongoTemplate para realizar agregaciones complejas en MongoDB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final MongoTemplate mongoTemplate;
    private final TramiteRepository tramiteRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final ProcessRepository processRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Obtiene el resumen de analíticas para una compañía
     * Incluye: trámites activos, completados este mes, duración promedio, procesos activos
     *
     * @param companyId ID de la compañía
     * @return AnalyticsSummaryDTO con métricas consolidadas
     */
    public AnalyticsSummaryDTO getSummary(String companyId) {
        log.info("Obteniendo resumen de analíticas para compañía: {}", companyId);

        try {
            // 1. Contar trámites activos
            Long totalActiveTramites = countActiveTramites(companyId);

            // 2. Contar trámites completados este mes
            Long totalCompletedThisMonth = countCompletedThisMonth(companyId);

            // 3. Calcular duración promedio de trámites completados
            Double avgDurationMinutes = calculateAverageDuration(companyId);

            // 4. Contar procesos activos
            Long totalActiveProcesses = countActiveProcesses(companyId);

            AnalyticsSummaryDTO summary = AnalyticsSummaryDTO.builder()
                    .totalActiveTramites(totalActiveTramites)
                    .totalCompletedThisMonth(totalCompletedThisMonth)
                    .avgDurationMinutes(avgDurationMinutes != null ? Math.round(avgDurationMinutes * 100.0) / 100.0 : 0.0)
                    .totalActiveProcesses(totalActiveProcesses)
                    .build();

            log.info("Resumen de analíticas obtenido exitosamente");
            return summary;

        } catch (Exception e) {
            log.error("Error al obtener resumen de analíticas para compañía: {}", companyId, e);
            throw new RuntimeException("Error al calcular analíticas: " + e.getMessage());
        }
    }

    /**
     * Obtiene analíticas detalladas por nodo para un proceso específico
     * Incluye: duración promedio por nodo, cantidad de ejecuciones
     *
     * @param processId ID del proceso
     * @return Lista de NodeAnalyticsDTO con métricas por nodo
     */
    public List<NodeAnalyticsDTO> getNodeAnalytics(String processId) {
        log.info("Obteniendo analíticas de nodos para proceso: {}", processId);

        try {
            // Validar que el proceso existe
            Process process = processRepository.findById(processId)
                    .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + processId));

            if (process.getNodes() == null || process.getNodes().isEmpty()) {
                log.warn("El proceso no tiene nodos definidos");
                return new ArrayList<>();
            }

            // Crear un mapa de nodeId -> nodeName para acceso rápido
            Map<String, String> nodeMap = process.getNodes().stream()
                    .collect(Collectors.toMap(Node::getId, Node::getLabel));

            // Usar aggregation pipeline de MongoDB
            List<AggregationOperation> operations = new ArrayList<>();

            // Match: solo tareas del proceso
            operations.add(
                    Aggregation.match(Criteria.where("processId").is(processId))
            );

            // Group: agrupar por nodeId y calcular promedio y contar
            operations.add(
                    Aggregation.group("nodeId")
                            .avg("durationMinutes").as("avgDurationMinutes")
                            .count().as("totalTasks")
            );

            // Project: renombrar _id a nodeId
            operations.add(
                    Aggregation.project()
                            .and("_id").as("nodeId")
                            .and("avgDurationMinutes").as("avgDurationMinutes")
                            .and("totalTasks").as("totalTasks")
            );

            // Sort: ordenar por totalTasks descendente
            operations.add(
                    Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "totalTasks")
            );

            Aggregation aggregation = Aggregation.newAggregation(operations);
            AggregationResults<Map> results = mongoTemplate.aggregate(
                    aggregation,
                    "task_submissions",
                    Map.class
            );

            // Transformar resultados a DTOs
            List<NodeAnalyticsDTO> nodeAnalytics = new ArrayList<>();
            for (Map result : results.getMappedResults()) {
                String nodeId = (String) result.get("nodeId");
                String nodeName = nodeMap.getOrDefault(nodeId, "Unknown Node");

                Double avgDuration = null;
                Object avgObj = result.get("avgDurationMinutes");
                if (avgObj != null) {
                    if (avgObj instanceof Double) {
                        avgDuration = (Double) avgObj;
                    } else if (avgObj instanceof Integer) {
                        avgDuration = ((Integer) avgObj).doubleValue();
                    }
                }

                Integer totalTasks = (Integer) result.get("totalTasks");

                NodeAnalyticsDTO dto = NodeAnalyticsDTO.builder()
                        .nodeId(nodeId)
                        .nodeName(nodeName)
                        .avgDurationMinutes(avgDuration != null ? Math.round(avgDuration * 100.0) / 100.0 : 0.0)
                        .totalTasks(totalTasks != null ? totalTasks.longValue() : 0L)
                        .build();

                nodeAnalytics.add(dto);
            }

            log.info("Analíticas de nodos obtenidas: {} nodos", nodeAnalytics.size());
            return nodeAnalytics;

        } catch (Exception e) {
            log.error("Error al obtener analíticas de nodos para proceso: {}", processId, e);
            throw new RuntimeException("Error al calcular analíticas de nodos: " + e.getMessage());
        }
    }

    /**
     * Obtiene los trámites activos de una compañía con detalles de espera
     * Calcula cuánto tiempo han estado esperando en su nodo actual
     *
     * @param companyId ID de la compañía
     * @return Lista de ActiveTramiteDTO con trámites activos
     */
    public List<ActiveTramiteDTO> getActiveTramites(String companyId) {
        log.info("Obteniendo trámites activos para compañía: {}", companyId);

        try {
            // Buscar todos los trámites activos
            List<Tramite> activeTramites = tramiteRepository.findAll().stream()
                    .filter(t -> TramiteStatus.ACTIVE.equals(t.getStatus()))
                    .collect(Collectors.toList());

            List<ActiveTramiteDTO> result = new ArrayList<>();

            for (Tramite tramite : activeTramites) {
                try {
                    // Obtener proceso
                    Process process = processRepository.findById(tramite.getProcessId())
                            .orElseThrow(() -> new RuntimeException("Proceso no encontrado"));

                    // Obtener nombre del nodo actual
                    String currentNodeName = getNodeName(process, tramite.getCurrentNodeId());

                    // Obtener nombre del departamento
                    String departmentName = getDepartmentName(tramite.getDepartmentId());

                    // Calcular minutos de espera
                    Long waitingMinutes = calculateWaitingMinutes(tramite);

                    ActiveTramiteDTO dto = ActiveTramiteDTO.builder()
                            .code(tramite.getCode())
                            .processName(process.getName())
                            .currentNode(currentNodeName)
                            .departmentName(departmentName)
                            .waitingMinutes(waitingMinutes)
                            .build();

                    result.add(dto);

                } catch (Exception e) {
                    log.warn("Error procesando trámite {}: {}", tramite.getId(), e.getMessage());
                    // Continuar con el siguiente trámite
                }
            }

            log.info("Se encontraron {} trámites activos", result.size());
            return result;

        } catch (Exception e) {
            log.error("Error al obtener trámites activos para compañía: {}", companyId, e);
            throw new RuntimeException("Error al obtener trámites activos: " + e.getMessage());
        }
    }

    // ============= MÉTODOS NUEVOS DE ANALÍTICA AVANZADA =============

    /**
     * Detecta cuellos de botella: nodos donde el tiempo promedio excede el SLA definido.
     * Fuente: task_submissions. Requiere que el nodo tenga slaHours configurado.
     *
     * @param companyId ID de la compañía
     * @return Lista de BottleneckDTO ordenada de mayor a menor exceso de SLA (en minutos)
     */
    public List<BottleneckDTO> getBottlenecks(String companyId) {
        log.info("Calculando cuellos de botella para compañía: {}", companyId);
        try {
            List<Process> processes = processRepository.findByCompanyId(companyId);
            if (processes.isEmpty()) return Collections.emptyList();

            // processId -> (nodeId -> slaMinutes) — solo nodos con SLA configurado
            Map<String, Map<String, Integer>> slaMap = buildSlaMap(processes);
            List<String> processIds = processes.stream().map(Process::getId).collect(Collectors.toList());

            // Agrupar task_submissions por (processId, nodeId) y calcular duración promedio
            List<AggregationOperation> ops = new ArrayList<>();
            ops.add(Aggregation.match(
                    Criteria.where("processId").in(processIds)
                            .and("durationMinutes").ne(null)
            ));
            ops.add(Aggregation.group("processId", "nodeId")
                    .avg("durationMinutes").as("avgDuration"));

            AggregationResults<Map> results = mongoTemplate.aggregate(
                    Aggregation.newAggregation(ops), "task_submissions", Map.class);

            List<BottleneckDTO> bottlenecks = new ArrayList<>();
            for (Map result : results.getMappedResults()) {
                // _id es un Document con processId y nodeId cuando se agrupa por campo compuesto
                Map<?, ?> idMap = (Map<?, ?>) result.get("_id");
                if (idMap == null) continue;

                String processId = (String) idMap.get("processId");
                String nodeId = (String) idMap.get("nodeId");
                Double avgDuration = toDouble(result.get("avgDuration"));
                if (processId == null || nodeId == null || avgDuration == null) continue;

                Map<String, Integer> nodeSla = slaMap.get(processId);
                if (nodeSla == null) continue;
                Integer slaMinutes = nodeSla.get(nodeId);
                if (slaMinutes == null) continue;

                // Solo reportar nodos donde el promedio real supera el SLA
                if (avgDuration > slaMinutes) {
                    bottlenecks.add(BottleneckDTO.builder()
                            .nodeId(nodeId)
                            .processId(processId)
                            .avgDuration(round2(avgDuration))
                            .sla(slaMinutes)
                            .excess(round2(avgDuration - slaMinutes))
                            .build());
                }
            }

            bottlenecks.sort((a, b) -> Double.compare(b.getExcess(), a.getExcess()));
            log.info("Cuellos de botella encontrados: {}", bottlenecks.size());
            return bottlenecks;

        } catch (Exception e) {
            log.error("Error al calcular cuellos de botella para compañía: {}", companyId, e);
            throw new RuntimeException("Error al calcular cuellos de botella: " + e.getMessage());
        }
    }

    /**
     * Cuenta trámites creados agrupados por día en los últimos N días.
     * Fuente: colección tramites. La fecha se formatea como "YYYY-MM-DD" mediante $dateToString.
     *
     * @param companyId ID de la compañía
     * @param days      Número de días hacia atrás (mínimo 1)
     * @return Lista de TramitesByDayDTO ordenada por fecha ascendente
     */
    public List<TramitesByDayDTO> getTramitesByDay(String companyId, int days) {
        log.info("Contando trámites por día para compañía: {}, últimos {} días", companyId, days);
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, days));

            List<AggregationOperation> ops = new ArrayList<>();
            ops.add(Aggregation.match(
                    Criteria.where("companyId").is(companyId)
                            .and("createdAt").gte(cutoff)
            ));
            // $dateToString no está disponible directamente en el API tipo-seguro de Spring,
            // por eso se usa una operación con Document crudo
            ops.add(ctx -> new Document("$project",
                    new Document("date", new Document("$dateToString",
                            new Document("format", "%Y-%m-%d").append("date", "$createdAt")))));
            ops.add(Aggregation.group("date").count().as("count"));
            ops.add(Aggregation.project().and("_id").as("date").and("count").as("count"));
            ops.add(Aggregation.sort(Sort.Direction.ASC, "date"));

            AggregationResults<Map> results = mongoTemplate.aggregate(
                    Aggregation.newAggregation(ops), "tramites", Map.class);

            List<TramitesByDayDTO> tramitesByDay = results.getMappedResults().stream()
                    .map(r -> TramitesByDayDTO.builder()
                            .date((String) r.get("date"))
                            .count(toLong(r.get("count")))
                            .build())
                    .collect(Collectors.toList());

            log.info("Trámites por día obtenidos: {} entradas", tramitesByDay.size());
            return tramitesByDay;

        } catch (Exception e) {
            log.error("Error al obtener trámites por día para compañía: {}", companyId, e);
            throw new RuntimeException("Error al obtener trámites por día: " + e.getMessage());
        }
    }

    /**
     * Rendimiento por departamento: tareas completadas, tiempo promedio y cumplimiento de SLA.
     * El SLA se obtiene del nodo correspondiente en el proceso (slaHours * 60 = slaMinutes).
     *
     * @param companyId ID de la compañía
     * @return Lista de DepartmentPerformanceDTO, uno por departamento
     */
    public List<DepartmentPerformanceDTO> getDepartmentPerformance(String companyId) {
        log.info("Calculando rendimiento por departamento para compañía: {}", companyId);
        try {
            List<Department> departments = departmentRepository.findByCompanyId(companyId);
            if (departments.isEmpty()) return Collections.emptyList();

            Map<String, Map<String, Integer>> slaMap = buildSlaMap(
                    processRepository.findByCompanyId(companyId));

            List<DepartmentPerformanceDTO> result = new ArrayList<>();
            for (Department dept : departments) {
                List<TaskSubmission> submissions =
                        taskSubmissionRepository.findByDepartmentIdAndCompletedAtIsNotNull(dept.getId());

                if (submissions.isEmpty()) {
                    result.add(DepartmentPerformanceDTO.builder()
                            .department(dept.getName())
                            .totalTasks(0L)
                            .avgTime(0.0)
                            .slaCompliance(0.0)
                            .build());
                    continue;
                }

                double avgTime = submissions.stream()
                        .filter(s -> s.getDurationMinutes() != null)
                        .mapToInt(TaskSubmission::getDurationMinutes)
                        .average().orElse(0.0);

                // Solo se evalúa cumplimiento de SLA en tareas con duración registrada
                List<TaskSubmission> withDuration = submissions.stream()
                        .filter(s -> s.getDurationMinutes() != null)
                        .collect(Collectors.toList());

                long slaOk = withDuration.stream().filter(s -> {
                    Map<String, Integer> nodeSla = slaMap.get(s.getProcessId());
                    if (nodeSla == null) return false;
                    Integer slaMinutes = nodeSla.get(s.getNodeId());
                    return slaMinutes != null && s.getDurationMinutes() <= slaMinutes;
                }).count();

                double slaCompliance = withDuration.isEmpty()
                        ? 0.0
                        : (double) slaOk / withDuration.size() * 100;

                result.add(DepartmentPerformanceDTO.builder()
                        .department(dept.getName())
                        .totalTasks((long) submissions.size())
                        .avgTime(round2(avgTime))
                        .slaCompliance(round2(slaCompliance))
                        .build());
            }

            log.info("Rendimiento por departamento calculado: {} departamentos", result.size());
            return result;

        } catch (Exception e) {
            log.error("Error al calcular rendimiento por departamento para compañía: {}", companyId, e);
            throw new RuntimeException("Error al calcular rendimiento por departamento: " + e.getMessage());
        }
    }

    /**
     * Distribución de trámites ACTIVOS por nodo dentro de cada proceso de la compañía.
     * Permite ver en qué punto del flujo están atascados los trámites en curso.
     *
     * @param companyId ID de la compañía
     * @return Lista de ProcessCompletionDTO, uno por proceso con trámites activos
     */
    public List<ProcessCompletionDTO> getProcessCompletion(String companyId) {
        log.info("Calculando distribución de trámites por nodo para compañía: {}", companyId);
        try {
            List<Process> processes = processRepository.findByCompanyId(companyId);
            if (processes.isEmpty()) return Collections.emptyList();

            List<String> processIds = processes.stream().map(Process::getId).collect(Collectors.toList());

            // Agrupar trámites activos por (processId, currentNodeId)
            List<AggregationOperation> ops = new ArrayList<>();
            ops.add(Aggregation.match(
                    Criteria.where("processId").in(processIds)
                            .and("status").is(TramiteStatus.ACTIVE)
            ));
            ops.add(Aggregation.group("processId", "currentNodeId").count().as("count"));

            AggregationResults<Map> results = mongoTemplate.aggregate(
                    Aggregation.newAggregation(ops), "tramites", Map.class);

            // Reagrupar en Java por processId para construir el DTO anidado
            Map<String, List<NodeCountDTO>> byProcess = new HashMap<>();
            for (Map result : results.getMappedResults()) {
                Map<?, ?> idMap = (Map<?, ?>) result.get("_id");
                if (idMap == null) continue;

                String processId = (String) idMap.get("processId");
                String nodeId = (String) idMap.get("currentNodeId");
                if (processId == null || nodeId == null) continue;

                byProcess.computeIfAbsent(processId, k -> new ArrayList<>())
                        .add(NodeCountDTO.builder()
                                .nodeId(nodeId)
                                .count(toLong(result.get("count")))
                                .build());
            }

            List<ProcessCompletionDTO> completion = byProcess.entrySet().stream()
                    .map(e -> ProcessCompletionDTO.builder()
                            .processId(e.getKey())
                            .nodes(e.getValue())
                            .build())
                    .collect(Collectors.toList());

            log.info("Distribución por proceso calculada: {} procesos con trámites activos", completion.size());
            return completion;

        } catch (Exception e) {
            log.error("Error al calcular distribución de trámites para compañía: {}", companyId, e);
            throw new RuntimeException("Error al calcular distribución de trámites: " + e.getMessage());
        }
    }

    // ============= MÉTODOS PRIVADOS DE UTILIDAD =============

    /**
     * Cuenta los trámites activos de una compañía
     */
    private Long countActiveTramites(String companyId) {
        return tramiteRepository.findAll().stream()
                .filter(t -> TramiteStatus.ACTIVE.equals(t.getStatus()))
                .count();
    }

    /**
     * Cuenta los trámites completados en el mes actual
     */
    private Long countCompletedThisMonth(String companyId) {
        YearMonth currentMonth = YearMonth.now();
        return tramiteRepository.findAll().stream()
                .filter(t -> TramiteStatus.COMPLETED.equals(t.getStatus()) || TramiteStatus.CANCELLED.equals(t.getStatus()))
                .filter(t -> t.getCompletedDate() != null)
                .filter(t -> YearMonth.from(t.getCompletedDate()).equals(currentMonth))
                .count();
    }

    /**
     * Calcula la duración promedio en minutos de trámites completados o cancelados
     */
    private Double calculateAverageDuration(String companyId) {
        try {
            return tramiteRepository.findAll().stream()
                    .filter(t -> (TramiteStatus.COMPLETED.equals(t.getStatus()) || TramiteStatus.CANCELLED.equals(t.getStatus()))
                            && t.getCreatedAt() != null && t.getCompletedDate() != null)
                    .mapToLong(t -> ChronoUnit.MINUTES.between(t.getCreatedAt(), t.getCompletedDate()))
                    .filter(d -> d >= 0)
                    .average()
                    .orElse(0.0);
        } catch (Exception e) {
            log.warn("Error al calcular duración promedio: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Cuenta los procesos no deprecados
     */
    private Long countActiveProcesses(String companyId) {
        return processRepository.findAll().stream()
                .filter(p -> !com.workflow.workflowplatform.model.enums.ProcessStatus.DEPRECATED
                        .equals(p.getStatus()))
                .count();
    }

    /**
     * Obtiene el nombre del nodo dado su ID
     */
    private String getNodeName(Process process, String nodeId) {
        if (process.getNodes() == null) {
            return "Unknown";
        }
        return process.getNodes().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .map(Node::getLabel)
                .findFirst()
                .orElse("Unknown");
    }

    /**
     * Obtiene el nombre del departamento
     */
    private String getDepartmentName(String departmentId) {
        if (departmentId == null) {
            return "N/A";
        }
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse("Unknown Department");
    }

    /**
     * Construye el mapa SLA: processId -> (nodeId -> slaMinutes).
     * Solo incluye nodos con slaHours > 0. Convierte horas a minutos.
     */
    private Map<String, Map<String, Integer>> buildSlaMap(List<Process> processes) {
        Map<String, Map<String, Integer>> slaMap = new HashMap<>();
        for (Process p : processes) {
            if (p.getNodes() == null) continue;
            Map<String, Integer> nodeSla = new HashMap<>();
            for (Node n : p.getNodes()) {
                if (n.getSlaHours() != null && n.getSlaHours() > 0) {
                    nodeSla.put(n.getId(), n.getSlaHours() * 60);
                }
            }
            slaMap.put(p.getId(), nodeSla);
        }
        return slaMap;
    }

    private Double toDouble(Object val) {
        if (val instanceof Double) return (Double) val;
        if (val instanceof Integer) return ((Integer) val).doubleValue();
        if (val instanceof Long) return ((Long) val).doubleValue();
        return null;
    }

    private Long toLong(Object val) {
        if (val instanceof Long) return (Long) val;
        if (val instanceof Integer) return ((Integer) val).longValue();
        return 0L;
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }

    /**
     * Calcula cuántos minutos lleva un trámite esperando en su nodo actual
     */
    private Long calculateWaitingMinutes(Tramite tramite) {
        // Buscar el último task submission del trámite
        List<TaskSubmission> submissions = taskSubmissionRepository.findByTramiteId(tramite.getId());

        if (submissions.isEmpty()) {
            // Si no hay submissions, usar el createdAt del trámite
            if (tramite.getCreatedAt() != null) {
                return ChronoUnit.MINUTES.between(tramite.getCreatedAt(), LocalDateTime.now());
            }
            return 0L;
        }

        // Ordenar por fecha de creación descendente y tomar el más reciente
        TaskSubmission lastSubmission = submissions.stream()
                .max((t1, t2) -> {
                    LocalDateTime d1 = t1.getCreatedAt() != null ? t1.getCreatedAt() : LocalDateTime.MIN;
                    LocalDateTime d2 = t2.getCreatedAt() != null ? t2.getCreatedAt() : LocalDateTime.MIN;
                    return d1.compareTo(d2);
                })
                .orElse(null);

        if (lastSubmission != null && lastSubmission.getCreatedAt() != null) {
            return ChronoUnit.MINUTES.between(lastSubmission.getCreatedAt(), LocalDateTime.now());
        }

        if (tramite.getCreatedAt() != null) {
            return ChronoUnit.MINUTES.between(tramite.getCreatedAt(), LocalDateTime.now());
        }

        return 0L;
    }
}
