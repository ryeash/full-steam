package com.fullsteam;

import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.games.BuilderManager;
import com.fullsteam.games.CaptureTheFlagManager;
import com.fullsteam.games.EliminationManager;
import com.fullsteam.games.EscortManager;
import com.fullsteam.games.FreeForAllManager;
import com.fullsteam.games.GunMasterManager;
import com.fullsteam.games.JuggernautManager;
import com.fullsteam.games.KingOfTheHillManager;
import com.fullsteam.games.LoneWolfManager;
import com.fullsteam.games.OddballManager;
import com.fullsteam.games.TeamDeathmatchManager;
import com.fullsteam.games.ZombieDefenseManager;
import com.fullsteam.model.ActiveGame;
import com.fullsteam.model.Player;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.fullsteam.Config.CLEANUP_INTERVAL_SECONDS;
import static com.fullsteam.Config.MAX_GLOBAL_PLAYERS;
import static com.fullsteam.GameWebSocketHandler.GAME_STATE_MANAGER_KEY;
import static com.fullsteam.GameWebSocketHandler.PLAYER_ID_KEY;

public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);

    private final Semaphore globalPlayerCountSemaphore = new Semaphore(MAX_GLOBAL_PLAYERS);
    private final Map<Long, ActiveGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, Supplier<AbstractGameStateManager>> gameMap = new LinkedHashMap<>();

    public GameLobby() {
        Config.EXECUTOR.scheduleAtFixedRate(this::cleanupEmptyGames, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Lobby maintenance task scheduled to run every {} seconds.", CLEANUP_INTERVAL_SECONDS);
        gameMap.put("Team Deathmatch", () -> new TeamDeathmatchManager(this));
        gameMap.put("Capture The Flag", () -> new CaptureTheFlagManager(this));
        gameMap.put("King Of The Hill", () -> new KingOfTheHillManager(this));
        gameMap.put("Elimination", () -> new EliminationManager(this));
        gameMap.put("Oddball", () -> new OddballManager(this));
        gameMap.put("Gun Master", () -> new GunMasterManager(this));
        gameMap.put("Juggernaut", () -> new JuggernautManager(this));
        gameMap.put("Escort", () -> new EscortManager(this));
        gameMap.put("Free For All", () -> new FreeForAllManager(this));
        gameMap.put("Lone Wolf", () -> new LoneWolfManager(this));
        gameMap.put("Builder", () -> new BuilderManager(this));
        gameMap.put("Zombie Defense", () -> new ZombieDefenseManager(this));
    }

    public List<ActiveGame> getActiveGames() {
        return new ArrayList<>(activeGames.values());
    }

    public List<String> getGameTypes() {
        return gameMap.keySet()
                .stream()
                .toList();
    }

    /**
     * This entire method is synchronized to prevent race conditions during matchmaking.
     * This ensures that two players cannot simultaneously create a new game or overfill an existing one.
     */
    public synchronized void joinGame(Channel channel, String gameIdStr, String gameTypeStr) {
        AbstractGameStateManager gameToJoin = null;

        // 1. Try to join by specific game ID
        if (gameIdStr != null && !gameIdStr.isEmpty() && !gameIdStr.equals("null")) {
            try {
                long gameId = Long.parseLong(gameIdStr);
                ActiveGame activeGame = activeGames.get(gameId);
                AbstractGameStateManager game = activeGame != null ? activeGame.getGame() : null;
                if (game != null) {
                    if (!game.isFull()) {
                        log.info("Player {} joining specific game by ID: {}", GameWebSocketHandler.playerId(channel), gameId);
                        joinGame(channel, game);
                        return; // Player has joined, matchmaking is complete.
                    } else {
                        log.warn("Player {} attempted to join full game {}. Will try to find another game of the same type.", GameWebSocketHandler.playerId(channel), gameId);
                        // If the requested game is full, we can try to find another of the same type.
                        gameTypeStr = game.getClass().getSimpleName();
                    }
                } else {
                    log.warn("Player {} attempted to join non-existent game {}. Will try to find a game by type if specified.", GameWebSocketHandler.playerId(channel), gameId);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid gameId format provided: '{}'. Ignoring.", gameIdStr);
            }
        }

        // 2. If no game found by ID, try to find/create by game type
        if (gameTypeStr != null && !gameTypeStr.isEmpty() && !gameTypeStr.equals("null")) {
            if (gameMap.containsKey(gameTypeStr)) {
                log.info("Player {} looking for game of type: {}", GameWebSocketHandler.playerId(channel), gameTypeStr);
                gameToJoin = findOrCreateGame(gameTypeStr);
            } else {
                log.warn("Unsupported game type requested: '{}'. Will find any available game.", gameTypeStr);
            }
        }

        // 3. If still no game, find any available game (default behavior)
        if (gameToJoin == null) {
            log.info("No specific game requested or found, finding any available game for player {}.", GameWebSocketHandler.playerId(channel));
            // Default to finding any game of the first type in rotation, which findOrCreateGame handles.
            gameToJoin = findOrCreateGame(gameMap.keySet().iterator().next());
        }

        // 4. Join the determined game
        joinGame(channel, gameToJoin);
    }

    public void spectateGame(Channel channel, String gameIdStr) {
        long gameId = Long.parseLong(gameIdStr);
        ActiveGame activeGame = activeGames.get(gameId);
        AbstractGameStateManager game = activeGame != null ? activeGame.getGame() : null;

        if (game != null) {
            synchronized (game) {
                if (!game.isSpectatorsFull()) {
                    game.addSpectator(channel);
                    // Associate the game and a spectator flag with the channel for cleanup on disconnect
                    channel.attr(GameWebSocketHandler.GAME_STATE_MANAGER_KEY).set(game);
                    channel.attr(GameWebSocketHandler.IS_SPECTATOR_KEY).set(true);
                    log.info("Channel {} is now spectating game {}", channel.id().asShortText(), gameId);
                } else {
                    log.warn("Spectator failed to join game {}: spectator slots are full.", gameId);
                    playerDisconnected(); // Decrement the count since the connection will be closed
                    channel.close();
                }
            }
        } else {
            log.warn("Spectator tried to join non-existent game {}", gameId);
            playerDisconnected(); // Decrement the count since the connection will be closed
            channel.close();
        }
    }

    public void joinGame(Channel ctx, AbstractGameStateManager game) {
        // Add the player to that specific game instance
        Long playerId = GameWebSocketHandler.playerId(ctx);
        Player player = game.addPlayer(playerId, ctx);
        // Associate the channel with its game and player ID for future lookups
        ctx.attr(GAME_STATE_MANAGER_KEY).set(game);
        ctx.attr(PLAYER_ID_KEY).set(playerId);
    }

    // Finds an available game or creates a new one
    public AbstractGameStateManager findOrCreateGame(String gameType) {
        // First, try to find a game with an open slot
        for (ActiveGame activeGame : activeGames.values()) {
            AbstractGameStateManager game = activeGame.getGame();
            if (!game.isFull() && gameType.equals(activeGame.getGameType())) {
                return game;
            }
        }

        Supplier<AbstractGameStateManager> gameBuilder = gameMap.get(gameType);
        if (gameBuilder != null) {
            AbstractGameStateManager newGame = gameBuilder.get();
            log.info("No available games. Creating new game with ID: {}", newGame.getGameId());
            newGame.startGameLoop(); // Each game has its own loop
            activeGames.put(newGame.getGameId(), new ActiveGame(gameType, newGame));
            return newGame;
        }
        throw new IllegalArgumentException("unsupported game type: " + gameType);
    }

    public void removeGame(Long gameId) {
        AbstractGameStateManager game = activeGames.remove(gameId).getGame();
        if (game != null) {
            game.shutdown(); // Method to stop the game loop
            log.info("Removed and shut down game {}", gameId);
        }
    }

    private void cleanupEmptyGames() {
        log.debug("Running cleanup task for empty games...");
        List<Long> gamesToRemove = new ArrayList<>();

        // First, identify all games that have no human players
        for (Map.Entry<Long, ActiveGame> entry : activeGames.entrySet()) {
            if (!entry.getValue().getGame().hasHumanPlayers()) {
                gamesToRemove.add(entry.getKey());
            }
        }

        // Then, remove them to avoid concurrent modification issues
        for (Long gameId : gamesToRemove) {
            log.info("Game {} has no human players. Removing from lobby.", gameId);
            Optional.ofNullable(activeGames.get(gameId))
                    .map(ActiveGame::getGame)
                    .filter(g -> !g.hasHumanPlayers())
                    .map(AbstractGameStateManager::getGameId)
                    .ifPresent(this::removeGame);
        }
    }

    public boolean tryAcceptNewPlayer() {
        return globalPlayerCountSemaphore.tryAcquire();
    }

    public void playerDisconnected() {
        globalPlayerCountSemaphore.release();
    }

    public int getGlobalPlayerCount() {
        return MAX_GLOBAL_PLAYERS - globalPlayerCountSemaphore.availablePermits();
    }
}
