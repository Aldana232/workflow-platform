package com.workflow.workflowplatform.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket puro (sin STOMP) para colaboración en tiempo real con Yjs.
 *
 * Responsabilidad única: retransmitir cada mensaje recibido a todos los
 * demás clientes del mismo processId. No interpreta el contenido — Yjs
 * se encarga de la CRDT y la convergencia de estado.
 *
 * Concurrencia:
 *  - ConcurrentHashMap: el mapa de salas es thread-safe para lecturas y escrituras.
 *  - CopyOnWriteArrayList: iteración segura sin ConcurrentModificationException
 *    aunque otro hilo elimine sesiones al mismo tiempo.
 *  - synchronized(target): WebSocketSession.sendMessage() NO es thread-safe.
 *    Dos hilos enviando al mismo target sin sincronía causan IllegalStateException
 *    ("WebSocket is already sending a message"). El bloqueo por objeto evita esto.
 */
@Slf4j
public class CollabWebSocketHandler extends TextWebSocketHandler {

    // processId → sesiones activas en esa "sala"
    private final Map<String, List<WebSocketSession>> rooms = new ConcurrentHashMap<>();

    // ─── Ciclo de vida de la sesión ───────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String processId = extractProcessId(session);
        rooms.computeIfAbsent(processId, k -> new CopyOnWriteArrayList<>()).add(session);
        log.debug("Collab CONNECT  pid={} sid={} peers={}",
                processId, session.getId(), rooms.get(processId).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("Collab transport error sid={}: {}", session.getId(), ex.getMessage());
        removeSession(session);
    }

    // ─── Broadcast de mensajes ────────────────────────────────────────────────

    /** Mensajes de texto (raramente usados por Yjs, pero soportados) */
    @Override
    protected void handleTextMessage(WebSocketSession sender, TextMessage message) {
        broadcast(sender, message);
    }

    /**
     * Yjs envía actualizaciones de documento como binario (Uint8Array).
     * TextWebSocketHandler cierra la conexión al recibir binario si no se
     * sobreescribe este método — por eso lo sobreescribimos explícitamente.
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession sender, BinaryMessage message) {
        String processId = extractProcessId(sender);
        List<WebSocketSession> peers = rooms.getOrDefault(processId, List.of());

        for (WebSocketSession target : peers) {
            if (isSelf(target, sender) || !target.isOpen()) continue;
            sendSafe(target, message);
        }
    }

    // ─── Privados ─────────────────────────────────────────────────────────────

    private void broadcast(WebSocketSession sender, TextMessage message) {
        String processId = extractProcessId(sender);
        List<WebSocketSession> peers = rooms.getOrDefault(processId, List.of());

        for (WebSocketSession target : peers) {
            if (isSelf(target, sender) || !target.isOpen()) continue;
            sendSafe(target, message);
        }
    }

    /**
     * Envío sincronizado por sesión destino.
     * El lock es sobre el objeto 'target', no global, para máxima concurrencia.
     */
    private void sendSafe(WebSocketSession target, org.springframework.web.socket.WebSocketMessage<?> message) {
        synchronized (target) {
            if (!target.isOpen()) return;
            try {
                target.sendMessage(message);
            } catch (IOException e) {
                log.warn("Error sending collab message to sid={}: {}", target.getId(), e.getMessage());
            }
        }
    }

    private void removeSession(WebSocketSession session) {
        String processId = extractProcessId(session);
        List<WebSocketSession> peers = rooms.get(processId);
        if (peers != null) {
            peers.remove(session);
            if (peers.isEmpty()) rooms.remove(processId);  // evitar memory leak en salas vacías
        }
        log.debug("Collab DISCONNECT pid={} sid={}", processId, session.getId());
    }

    private boolean isSelf(WebSocketSession target, WebSocketSession sender) {
        return target.getId().equals(sender.getId());
    }

    /**
     * Extrae el processId del path URI de la sesión.
     * Con path /collab/{processId} → devuelve el segmento final.
     */
    private String extractProcessId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "/collab/";
        String id = path.replaceFirst("^.*/collab/", "").split("\\?")[0]; // quitar query string si existe
        return id.isEmpty() ? "default" : id;
    }
}
