package com.workflow.workflowplatform.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI Service que recibe un systemPrompt construido dinámicamente
 * y la pregunta del funcionario, y retorna la respuesta del LLM.
 *
 * El bean se registra en LangChain4jConfig sólo si openai.api.key está configurada.
 */
public interface AssistantAiService {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("{{question}}")
    String answer(@V("systemPrompt") String systemPrompt, @V("question") String question);
}
