package com.workflow.workflowplatform.config;

import com.workflow.workflowplatform.service.AssistantAiService;
import com.workflow.workflowplatform.service.VoiceCommandAiService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configura el modelo OpenAI y el AI Service de LangChain4j.
 * Los beans se crean sólo si la propiedad 'openai.api.key' está definida.
 * Si no está configurada, VoiceCommandService usa el parser regex como fallback.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "openai.api.key")
public class LangChain4jConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String modelName;

    @Value("${openai.temperature:0.0}")
    private double temperature;

    @Value("${openai.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        log.info("[LangChain4j] Iniciando modelo OpenAI: {} (timeout={}s)", modelName, timeoutSeconds);
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .temperature(temperature)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    @Bean
    public VoiceCommandAiService voiceCommandAiService(OpenAiChatModel model) {
        return AiServices.create(VoiceCommandAiService.class, model);
    }

    @Bean
    public AssistantAiService assistantAiService(OpenAiChatModel model) {
        return AiServices.create(AssistantAiService.class, model);
    }
}
