package com.workflow.workflowplatform.service;

import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.model.enums.ProcessStatus;
import com.workflow.workflowplatform.repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProcessService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessService.class);
    private final ProcessRepository processRepository;

    /**
     * Crea un nuevo proceso con estado DRAFT, versión 1 y sin trámites activos
     * 
     * @param process el proceso a crear
     * @return el proceso guardado con valores iniciales configurados
     */
    public Process createProcess(Process process) {
        logger.info("Iniciando creación de proceso: {}", process.getName());
        
        // Validaciones
        if (process.getName() == null || process.getName().isEmpty()) {
            logger.error("Error: El nombre del proceso es obligatorio");
            throw new RuntimeException("El nombre del proceso es obligatorio");
        }
        
        if (process.getCompanyId() == null || process.getCompanyId().isEmpty()) {
            process.setCompanyId("saguapac");
        }

        if (process.getNodes() == null || process.getNodes().isEmpty()) {
            process.setNodes(java.util.Collections.emptyList());
        }
        
        logger.info("Validaciones pasadas. Nodos: {}, Edges: {}", 
                process.getNodes().size(), 
                process.getEdges() != null ? process.getEdges().size() : 0);
        
        process.setStatus(ProcessStatus.DRAFT);
        process.setVersion(1);
        process.setActiveTramites(0);
        process.setCreatedAt(LocalDateTime.now());
        process.setUpdatedAt(LocalDateTime.now());

        Process savedProcess = processRepository.save(process);
        logger.info("Proceso creado exitosamente con ID: {}", savedProcess.getId());
        
        return savedProcess;
    }

    /**
     * Publica un proceso cambiando su estado a ACTIVE
     * Solo permite publicar si no hay trámites activos
     * 
     * @param processId id del proceso a publicar
     * @return el proceso publicado
     * @throws RuntimeException si el proceso no existe o tiene trámites activos
     */
    public Process updateProcess(String id, Process updates) {
        Process existing = processRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con id: " + id));

        if (updates.getName() != null && !updates.getName().isEmpty())
            existing.setName(updates.getName());
        if (updates.getBpmnXml() != null)
            existing.setBpmnXml(updates.getBpmnXml());
        if (updates.getNodes() != null)
            existing.setNodes(updates.getNodes());
        if (updates.getEdges() != null)
            existing.setEdges(updates.getEdges());
        if (updates.getNodeProperties() != null)
            existing.setNodeProperties(updates.getNodeProperties());
        existing.setUpdatedAt(LocalDateTime.now());

        return processRepository.save(existing);
    }

    public Process publishProcess(String processId) {
        Process process = processRepository.findById(processId)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con id: " + processId));

        if (process.getActiveTramites() > 0) {
            throw new RuntimeException("No se puede publicar el proceso. Existen " 
                    + process.getActiveTramites() + " trámites activos");
        }

        process.setStatus(ProcessStatus.ACTIVE);
        process.setUpdatedAt(LocalDateTime.now());

        return processRepository.save(process);
    }

    /**
     * Obtiene todos los procesos de una empresa específica
     * 
     * @param companyId id de la empresa
     * @return lista de procesos de la empresa
     */
    public List<Process> getProcessesByCompany(String companyId) {
        return processRepository.findByCompanyId(companyId);
    }

    public List<Process> getAllProcesses() {
        return processRepository.findAll();
    }

    /**
     * Obtiene un proceso específico por su id
     * 
     * @param id id del proceso
     * @return el proceso completo con nodos y aristas
     * @throws RuntimeException si el proceso no existe
     */
    public Process getProcessById(String id) {
        return processRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con id: " + id));
    }

    public Process deactivateProcess(String processId) {
        Process process = processRepository.findById(processId)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado: " + processId));
        process.setStatus(ProcessStatus.DRAFT);
        process.setUpdatedAt(LocalDateTime.now());
        return processRepository.save(process);
    }

    public void deleteProcess(String id) {
        Process process = processRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con id: " + id));
        if (process.getActiveTramites() != null && process.getActiveTramites() > 0) {
            throw new RuntimeException("No se puede eliminar: el proceso tiene " + process.getActiveTramites() + " trámites activos");
        }
        processRepository.deleteById(id);
        logger.info("Proceso eliminado con ID: {}", id);
    }
}
