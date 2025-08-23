package com.fullsteam.websocket;

import com.fullsteam.service.PlayerConnectionService;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
    @OnClose
    public void onClose(WebSocketSession session) {
        connectionService.disconnectSpectator(session.getId());
    }
}
