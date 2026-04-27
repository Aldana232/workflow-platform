package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.model.Process;
import com.workflow.workflowplatform.service.ProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> createProcess(@RequestBody Process process) {
        try {
            Process createdProcess = processService.createProcess(process);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdProcess);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage())
            );
        }
    }

    /**
     * Publica un proceso existente
     * Cambia el estado de DRAFT a ACTIVE
     * 
     * @param id id del proceso a publicar
     * @return ResponseEntity con el proceso publicado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<?> updateProcess(@PathVariable String id, @RequestBody Process process) {
        try {
            Process updated = processService.updateProcess(id, process);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Process> publishProcess(@PathVariable String id) {
        try {
            Process publishedProcess = processService.publishProcess(id);
            return ResponseEntity.ok(publishedProcess);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtiene todos los procesos de una empresa
     * 
     * @param companyId id de la empresa
     * @return ResponseEntity con lista de procesos
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<List<Process>> getProcessesByCompany(
            @RequestParam(required = false) String companyId) {
        List<Process> processes = (companyId != null && !companyId.isEmpty())
                ? processService.getProcessesByCompany(companyId)
                : processService.getAllProcesses();
        return ResponseEntity.ok(processes);
    }

    /**
     * Obtiene un proceso específico por su id
     * Incluye todos los nodos y aristas del flujo
     * 
     * @param id id del proceso
     * @return ResponseEntity con el proceso completo
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN', 'FUNCIONARIO')")
    public ResponseEntity<Process> getProcessById(@PathVariable String id) {
        try {
            Process process = processService.getProcessById(id);
            return ResponseEntity.ok(process);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Process> deactivateProcess(@PathVariable String id) {
        try {
            return ResponseEntity.ok(processService.deactivateProcess(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPERADMIN', 'ADMIN')")
    public ResponseEntity<Void> deleteProcess(@PathVariable String id) {
        try {
            processService.deleteProcess(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
