package com.fullsteam.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fullsteam.Jackson;
import com.fullsteam.service.PlayerConnectionService;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@ServerWebSocket("/game/{gameId}/spectate")
public class SpectatorWebSocketEndpoint {
    
    private static final Logger log = LoggerFactory.getLogger(SpectatorWebSocketEndpoint.class);
    
    private final PlayerConnectionService connectionService;
    
    @Inject
    public SpectatorWebSocketEndpoint(PlayerConnectionService connectionService) {
        this.connectionService = connectionService;
    }
    
    @OnOpen
    public void onOpen(WebSocketSession session, String gameId) {
        if (!connectionService.connectSpectator(session, gameId)) {
            session.close();
        }
    }
    
    @OnMessage
    public void onMessage(String message, WebSocketSession session) {
        try {
            JsonNode rootNode = Jackson.readTree(message);
            String type = rootNode.path("type").asText("");
            
            // Handle ping messages for spectators
            if ("ping".equals(type)) {
                session.sendSync(Jackson.writeValueAsString(Map.of("type", "pong")));
            } else {
                log.debug("Received message of type '{}' from spectator {}, ignoring", type, session.getId());
            }
        } catch (Exception e) {
            log.error("Error processing message from spectator {}: {}", session.getId(), e.getMessage());
        }
    }
    
    @OnClose
    public void onClose(WebSocketSession session) {
        connectionService.disconnectSpectator(session.getId());
    }
}
