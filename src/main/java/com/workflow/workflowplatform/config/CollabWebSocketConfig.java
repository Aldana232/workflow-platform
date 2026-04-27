package com.workflow.workflowplatform.config;

import com.workflow.workflowplatform.websocket.CollabWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registra el endpoint WebSocket puro /collab/{processId} para colaboración
 * en tiempo real (Yjs CRDT).
 *
 * NO usa @EnableWebSocket para evitar el conflicto de bean 'webSocketHandlerMapping'
 * con el @EnableWebSocketMessageBroker ya presente en WebSocketConfig.
 * En su lugar se registra directamente con SimpleUrlHandlerMapping +
 * WebSocketHttpRequestHandler, que Spring MVC resuelve correctamente.
 *
 * CORS: En Spring WebSocket 6.2.x, ni WebSocketHttpRequestHandler ni DefaultHandshakeHandler
 * exponen setAllowedOrigins de forma directa en tiempo de compilación del IDE.
 * El punto de extensión correcto es sobrescribir isValidOrigin() en AbstractHandshakeHandler,
 * que es el método protected que Spring llama internamente durante el handshake HTTP→WS.
 */
@Configuration
public class CollabWebSocketConfig {

    @Bean
    public CollabWebSocketHandler collabWebSocketHandler() {
        return new CollabWebSocketHandler();
    }

    @Bean(name = "collabWebSocketHandlerMapping")
    public SimpleUrlHandlerMapping collabWebSocketHandlerMapping(CollabWebSocketHandler handler) {

        /*
         * isValidOrigin() es el método protected de AbstractHandshakeHandler
         * que valida el header Origin antes de completar el upgrade HTTP→WebSocket.
         * Sobrescribirlo es el mecanismo oficial para CORS en WebSocket puro.
         *
         * En producción: reemplaza "return true" por una validación real, ej:
         *   String origin = request.getHeaders().getOrigin();
         *   return "https://tu-dominio.com".equals(origin);
         */
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler() {
            @Override
            protected boolean isValidOrigin(ServerHttpRequest request) {
                return true;
            }
        };

        WebSocketHttpRequestHandler requestHandler =
                new WebSocketHttpRequestHandler(handler, handshakeHandler);

        Map<String, Object> urlMap = new LinkedHashMap<>();
        urlMap.put("/collab/**", requestHandler);

        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(urlMap);
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return mapping;
    }

    /**
     * Ajusta los límites de buffer para las sesiones WebSocket de todo el servidor.
     * Yjs envía snapshots completos del documento; sin este bean Tomcat usa 8 KB
     * por defecto, lo que trunca actualizaciones grandes y corrompe el estado CRDT.
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(512 * 1024);    // 512 KB
        container.setMaxBinaryMessageBufferSize(1024 * 1024); // 1 MB — Yjs state vectors
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L);  // 30 min inactividad
        return container;
    }
}
