package com.workflow.workflowplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public Map<String, Object> getBottlenecks(String processId) {
        try {
            String url = aiServiceUrl + "/api/analytics/bottlenecks/" + processId;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : new HashMap<>();
        } catch (Exception e) {
            log.warn("AI service unavailable for getBottlenecks({}): {}", processId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "AI service unavailable");
            return error;
        }
    }

    public Map<String, Object> detectAnomalies(String companyId) {
        try {
            String url = aiServiceUrl + "/api/anomalies/detect/" + companyId;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : new HashMap<>();
        } catch (Exception e) {
            log.warn("AI service unavailable for detectAnomalies({}): {}", companyId, e.getMessage());
            return new HashMap<>();
        }
    }

    public Map<String, Object> getPriorityTramites(String companyId) {
        try {
            String url = aiServiceUrl + "/api/predictions/priority/" + companyId;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : new HashMap<>();
        } catch (Exception e) {
            log.warn("AI service unavailable for getPriorityTramites({}): {}", companyId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "AI service unavailable");
            return error;
        }
    }

    public Map<String, Object> recommendPolicy(String description, String companyId) {
        try {
            String url = aiServiceUrl + "/api/predictions/policy";
            Map<String, String> body = new HashMap<>();
            body.put("description", description);
            body.put("company_id", companyId);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);
            return response != null ? response : new HashMap<>();
        } catch (Exception e) {
            log.warn("AI service unavailable for recommendPolicy(company={}): {}", companyId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "AI service unavailable");
            return error;
        }
    }

    public Map<String, Object> getNextAction(String tramiteId) {
        try {
            String url = aiServiceUrl + "/api/recommendations/next-action/" + tramiteId;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return response != null ? response : new HashMap<>();
        } catch (Exception e) {
            log.warn("AI service unavailable for getNextAction({}): {}", tramiteId, e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "AI service unavailable");
            return error;
        }
    }
}
