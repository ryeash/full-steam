package com.fullsteam.service;

import com.fullsteam.GameLobby;
import com.fullsteam.config.GameConfig;
import com.fullsteam.games.AbstractGameStateManager;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service to manage player connections and bridge between WebSocket sessions and game logic
 */
@Singleton
public class PlayerConnectionService {
    
    private static final Logger log = LoggerFactory.getLogger(PlayerConnectionService.class);
    
    private final GameLobby gameLobby;
    private final GameConfig gameConfig;
    private final AtomicLong playerIdGenerator = new AtomicLong(System.currentTimeMillis());
    
    // Session management
    private final Map<String, PlayerSession> playerSessions = new ConcurrentHashMap<>();
    private final Map<String, SpectatorSession> spectatorSessions = new ConcurrentHashMap<>();
    
    @Inject
    public PlayerConnectionService(GameLobby gameLobby, GameConfig gameConfig) {
        this.gameLobby = gameLobby;
        this.gameConfig = gameConfig;
    }
    
    /**
     * Handle a new player connection
     */
    public boolean connectPlayer(WebSocketSession session, String gameId, String gameType) {
        String sessionId = session.getId();
        
        // Check if server can accept new players
        if (!gameLobby.tryAcceptNewPlayer()) {
            log.warn("Server is full ({} players). Rejecting new connection.", 
                    gameConfig.getMaxGlobalPlayers());
            return false;
        }
        
        try {
            // Decode the game type from URL
            String decodedGameType = URLDecoder.decode(gameType, StandardCharsets.UTF_8);
            
            // Generate player ID
            Long playerId = playerIdGenerator.incrementAndGet();
            
            // Join the game
            AbstractGameStateManager game = joinGame(gameId, decodedGameType);
            
            // Create a bridge channel for the existing game code
            WebSocketChannelBridge channelBridge = new WebSocketChannelBridge(sessionId, session, playerId);
            
            // Add player to the game using the bridge
            game.addPlayer(playerId, channelBridge);
            
            // Store session info
            playerSessions.put(sessionId, new PlayerSession(game, playerId, channelBridge));
            
            log.info("Player {} joined game {} of type {}", playerId, game.getGameId(), decodedGameType);
            return true;
            
        } catch (Exception e) {
            log.error("Error during player connection setup", e);
            gameLobby.playerDisconnected();
            return false;
        }
    }
    
    /**
     * Handle a new spectator connection
     */
    public boolean connectSpectator(WebSocketSession session, String gameId) {
        String sessionId = session.getId();
        
        // Check if server can accept new connections
        if (!gameLobby.tryAcceptNewPlayer()) {
            log.warn("Server is full ({} players). Rejecting new spectator connection.", 
                    gameConfig.getMaxGlobalPlayers());
            return false;
        }
        
        try {
            long gameIdLong = Long.parseLong(gameId);
            AbstractGameStateManager game = gameLobby.findGameById(gameIdLong);
            
            if (game != null) {
                if (!game.isSpectatorsFull()) {
                    // Create a bridge channel for spectator
                    WebSocketChannelBridge channelBridge = new WebSocketChannelBridge(sessionId, session, null);
                    
                    game.addSpectator(channelBridge);
                    spectatorSessions.put(sessionId, new SpectatorSession(game, channelBridge));
                    
                    log.info("Spectator {} is now watching game {}", sessionId, gameIdLong);
                    return true;
                } else {
                    log.warn("Spectator failed to join game {}: spectator slots are full.", gameIdLong);
                    gameLobby.playerDisconnected();
                    return false;
                }
            } else {
                log.warn("Spectator tried to join non-existent game {}", gameIdLong);
                gameLobby.playerDisconnected();
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("Invalid game ID format for spectator: {}", gameId);
            gameLobby.playerDisconnected();
            return false;
        } catch (Exception e) {
            log.error("Error during spectator connection setup", e);
            gameLobby.playerDisconnected();
            return false;
        }
    }
    
    /**
     * Handle player disconnection
     */
    public void disconnectPlayer(String sessionId) {
        PlayerSession playerSession = playerSessions.remove(sessionId);
        
        if (playerSession != null) {
            AbstractGameStateManager game = playerSession.getGame();
            if (game != null) {
                Long playerId = playerSession.getPlayerId();
                if (playerId != null) {
                    game.removePlayer(playerId);
                    log.info("Player {} disconnected from game {}", playerId, game.getGameId());
                }
            }
            gameLobby.playerDisconnected();
            log.info("Player connection closed. Global players: {}", gameLobby.getGlobalPlayerCount());
        }
    }
    
    /**
     * Handle spectator disconnection
     */
    public void disconnectSpectator(String sessionId) {
        SpectatorSession spectatorSession = spectatorSessions.remove(sessionId);
        
        if (spectatorSession != null) {
            AbstractGameStateManager game = spectatorSession.getGame();
            if (game != null) {
                game.removeSpectator(spectatorSession.getChannelBridge());
                gameLobby.playerDisconnected();
                log.info("Spectator {} disconnected from game {}", sessionId, game.getGameId());
            }
        }
    }
    
    /**
     * Get player session for message handling
     */
    public PlayerSession getPlayerSession(String sessionId) {
        return playerSessions.get(sessionId);
    }
    
    private AbstractGameStateManager joinGame(String gameIdStr, String gameType) {
        AbstractGameStateManager gameToJoin = null;
        
        // Try to join by specific game ID
        if (gameIdStr != null && !gameIdStr.isEmpty() && !gameIdStr.equals("null")) {
            try {
                long gameId = Long.parseLong(gameIdStr);
                gameToJoin = gameLobby.findGameById(gameId);
                if (gameToJoin != null && gameToJoin.isFull()) {
                    log.warn("Attempted to join full game {}. Will try to find another game of the same type.", gameId);
                    gameToJoin = null;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid gameId format provided: '{}'. Ignoring.", gameIdStr);
            }
        }
        
        // If no game found by ID, try to find/create by game type
        if (gameToJoin == null && gameType != null && !gameType.isEmpty() && !gameType.equals("null")) {
            gameToJoin = gameLobby.findOrCreateGame(gameType);
        }
        
        // If still no game, find any available game
        if (gameToJoin == null) {
            gameToJoin = gameLobby.findOrCreateGame(gameLobby.getGameTypes().get(0));
        }
        
        return gameToJoin;
    }
    
    /**
     * Player session information
     */
    public static class PlayerSession {
        private final AbstractGameStateManager game;
        private final Long playerId;
        private final WebSocketChannelBridge channelBridge;
        
        public PlayerSession(AbstractGameStateManager game, Long playerId, WebSocketChannelBridge channelBridge) {
            this.game = game;
            this.playerId = playerId;
            this.channelBridge = channelBridge;
        }
        
        public AbstractGameStateManager getGame() { return game; }
        public Long getPlayerId() { return playerId; }
        public WebSocketChannelBridge getChannelBridge() { return channelBridge; }
    }
    
    /**
     * Spectator session information
     */
    public static class SpectatorSession {
        private final AbstractGameStateManager game;
        private final WebSocketChannelBridge channelBridge;
        
        public SpectatorSession(AbstractGameStateManager game, WebSocketChannelBridge channelBridge) {
            this.game = game;
            this.channelBridge = channelBridge;
        }
        
        public AbstractGameStateManager getGame() { return game; }
        public WebSocketChannelBridge getChannelBridge() { return channelBridge; }
    }
}
