package com.fullsteam.controller;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.model.PlayerSession;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage player connections and bridge between WebSocket sessions and game logic
 */
@Singleton
public class PlayerConnectionService {

    public static final String SESSION_KEY = "fullsteam.playerSession";

    private static final Logger log = LoggerFactory.getLogger(PlayerConnectionService.class);

    private final GameLobby gameLobby;

    // Session management
    private final Map<String, SpectatorSession> spectatorSessions = new ConcurrentHashMap<>();

    @Inject
    public PlayerConnectionService(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
    }

    /**
     * Handle a new player connection
     */
    public boolean connectPlayer(WebSocketSession session, String gameId, String gameType) {
        // Check if server can accept new players
        if (!gameLobby.tryAcceptNewPlayer()) {
            log.warn("Server is full ({} players). Rejecting new connection.", Config.MAX_GLOBAL_PLAYERS);
            return false;
        }

        try {
            String decodedGameType = URLDecoder.decode(gameType, StandardCharsets.UTF_8);
            AbstractGameStateManager game = joinGame(gameId, decodedGameType);
            PlayerSession ps = game.addPlayer(Config.ID_COUNTER.incrementAndGet(), session);
            session.put(SESSION_KEY, ps);
            log.info("Player {} joined game {} of type {}", ps.getPlayer().id(), game.getGameId(), decodedGameType);
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
            log.warn("Server is full ({} players). Rejecting new spectator connection.", Config.MAX_GLOBAL_PLAYERS);
            return false;
        }

        try {
            long gameIdLong = Long.parseLong(gameId);
            AbstractGameStateManager game = gameLobby.findGameById(gameIdLong);

            if (game != null) {
                if (!game.isSpectatorsFull()) {
                    // Create a bridge channel for spectator
                    game.addSpectator(session);
                    SpectatorSession spectatorSession = new SpectatorSession(game, session);
                    spectatorSessions.put(sessionId, new SpectatorSession(game, session));
                    session.put(SESSION_KEY, spectatorSession);

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
    public void disconnectPlayer(WebSocketSession session) {
        PlayerSession playerSession = session.get(SESSION_KEY, PlayerSession.class).orElse(null);
        if (playerSession != null) {
            AbstractGameStateManager game = playerSession.getGame();
            if (game != null) {
                Long playerId = playerSession.getPlayerId();
                game.removePlayer(playerId);
                log.info("Player {} disconnected from game {}", playerId, game.getGameId());
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
            gameToJoin = gameLobby.findOrCreateGame(gameLobby.getGameTypes().getFirst());
        }

        return gameToJoin;
    }

    /**
     * Spectator session information
     */
    public static class SpectatorSession {
        private final AbstractGameStateManager game;
        private final WebSocketSession channelBridge;

        public SpectatorSession(AbstractGameStateManager game, WebSocketSession channelBridge) {
            this.game = game;
            this.channelBridge = channelBridge;
        }

        public AbstractGameStateManager getGame() {
            return game;
        }

        public WebSocketSession getChannelBridge() {
            return channelBridge;
        }
    }
}
