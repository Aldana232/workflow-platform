package com.workflow.workflowplatform.controller;

import com.workflow.workflowplatform.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @GetMapping("/bottlenecks/{processId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getBottlenecks(@PathVariable String processId) {
        return ResponseEntity.ok(aiService.getBottlenecks(processId));
    }

    @GetMapping("/anomalies/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> detectAnomalies(@PathVariable String companyId) {
        return ResponseEntity.ok(aiService.detectAnomalies(companyId));
    }

    @GetMapping("/priority/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getPriorityTramites(@PathVariable String companyId) {
        return ResponseEntity.ok(aiService.getPriorityTramites(companyId));
    }

    @PostMapping("/recommend-policy")
    public ResponseEntity<Map<String, Object>> recommendPolicy(@RequestBody Map<String, String> body) {
        String description = body.get("description");
        String companyId = body.get("companyId");
        return ResponseEntity.ok(aiService.recommendPolicy(description, companyId));
    }

    @GetMapping("/next-action/{tramiteId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FUNCIONARIO', 'SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getNextAction(@PathVariable String tramiteId) {
        return ResponseEntity.ok(aiService.getNextAction(tramiteId));
    }

    @GetMapping("/report/by-date")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getReportByDate(
            @RequestParam String fromDate,
            @RequestParam String toDate) {
        return ResponseEntity.ok(aiService.getReportByDate(fromDate, toDate));
    }

    @GetMapping("/report/by-client")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getReportByClient(@RequestParam String name) {
        return ResponseEntity.ok(aiService.getReportByClient(name));
    }

    @GetMapping("/report/by-process")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<Map<String, Object>> getSummaryByProcess() {
        return ResponseEntity.ok(aiService.getSummaryByProcess());
    }
}
