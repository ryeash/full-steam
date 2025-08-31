package com.fullsteam.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.fullsteam.controller.PlayerConnectionService.SESSION_KEY;

@ServerWebSocket("/game/{gameId}/spectate")
public class SpectatorWebSocketEndpoint {

    private static final Logger log = LoggerFactory.getLogger(SpectatorWebSocketEndpoint.class);

    private final PlayerConnectionService connectionService;
    private final ObjectMapper objectMapper;

    @Inject
    public SpectatorWebSocketEndpoint(PlayerConnectionService connectionService, ObjectMapper objectMapper) {
        this.connectionService = connectionService;
        this.objectMapper = objectMapper;
    }

    @OnOpen
    public void onOpen(WebSocketSession session, String gameId) {
        if (!connectionService.connectSpectator(session, gameId)) {
            session.close();
        }
    }

    @OnMessage
    public void onMessage(byte[] message, WebSocketSession session) {
        try {
            Map<?, ?> map = objectMapper.readValue(message, Map.class);
            String type = (String) map.get("type");
            if (type.equals("ping")) {
                session.get(SESSION_KEY, PlayerConnectionService.SpectatorSession.class)
                        .map(PlayerConnectionService.SpectatorSession::getGame)
                        .ifPresent(game -> game.send(session, Map.of("type", "pong")));
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
