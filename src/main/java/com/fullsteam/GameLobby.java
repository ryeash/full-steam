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
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.WelcomeMessage;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.fullsteam.Config.CLEANUP_INTERVAL_SECONDS;
import static com.fullsteam.Config.MAX_GLOBAL_PLAYERS;
import static com.fullsteam.GameWebSocketHandler.GAME_STATE_MANAGER_KEY;
import static com.fullsteam.GameWebSocketHandler.PLAYER_ID_KEY;

public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);

    private final AtomicInteger globalPlayerCount = new AtomicInteger(0);
    private final Map<Long, AbstractGameStateManager> activeGames = new ConcurrentHashMap<>();
    private final List<GameMode> GAME_ROTATION = new ArrayList<>();

    record GameMode(Class<? extends AbstractGameStateManager> type, Supplier<AbstractGameStateManager> builder) {
    }

    public GameLobby() {
        Config.EXECUTOR.scheduleAtFixedRate(this::cleanupEmptyGames, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Lobby maintenance task scheduled to run every {} seconds.", CLEANUP_INTERVAL_SECONDS);
        addGameMode(TeamDeathmatchManager.class, () -> new TeamDeathmatchManager(this));
        addGameMode(CaptureTheFlagManager.class, () -> new CaptureTheFlagManager(this));
        addGameMode(KingOfTheHillManager.class, () -> new KingOfTheHillManager(this));
        addGameMode(EliminationManager.class, () -> new EliminationManager(this));
        addGameMode(OddballManager.class, () -> new OddballManager(this));
        addGameMode(GunMasterManager.class, () -> new GunMasterManager(this));
        addGameMode(JuggernautManager.class, () -> new JuggernautManager(this));
        addGameMode(EscortManager.class, () -> new EscortManager(this));
        addGameMode(FreeForAllManager.class, () -> new FreeForAllManager(this));
        addGameMode(LoneWolfManager.class, () -> new LoneWolfManager(this));
        addGameMode(BuilderManager.class, () -> new BuilderManager(this));
        addGameMode(ZombieDefenseManager.class, () -> new ZombieDefenseManager(this));
    }

    public List<ActiveGame> getActiveGames() {
        return activeGames.values().stream()
                .map(game -> new ActiveGame(
                        game.getGameId(),
                        game.gameType(),
                        game.getPlayerCount(),
                        game.getMaxPlayers()))
                .collect(Collectors.toList());
    }

    public List<String> getGameTypes() {
        return GAME_ROTATION.stream()
                .map(GameMode::type)
                .map(Class::getSimpleName)
                .toList();
    }

    public void addGameMode(Class<? extends AbstractGameStateManager> type, Supplier<AbstractGameStateManager> builder) {
        GAME_ROTATION.add(new GameMode(type, builder));
    }

    public void joinGame(Channel channel, String gameIdStr, String gameTypeStr) {
        AbstractGameStateManager gameToJoin = null;

        // 1. Try to join by specific game ID
        if (gameIdStr != null && !gameIdStr.isEmpty() && !gameIdStr.equals("null")) {
            try {
                long gameId = Long.parseLong(gameIdStr);
                AbstractGameStateManager game = activeGames.get(gameId);
                if (game != null) {
                    if (!game.isFull()) {
                        gameToJoin = game;
                        log.info("Player {} joining specific game by ID: {}", GameWebSocketHandler.playerId(channel), gameId);
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
        if (gameToJoin == null && gameTypeStr != null && !gameTypeStr.isEmpty() && !gameTypeStr.equals("null")) {
            Class<? extends AbstractGameStateManager> gameTypeClass = findGameTypeClass(gameTypeStr);
            if (gameTypeClass != null) {
                log.info("Player {} looking for game of type: {}", GameWebSocketHandler.playerId(channel), gameTypeStr);
                gameToJoin = findOrCreateGame(gameTypeClass);
            } else {
                log.warn("Unsupported game type requested: '{}'. Will find any available game.", gameTypeStr);
            }
        }

        // 3. If still no game, find any available game (default behavior)
        if (gameToJoin == null) {
            log.info("No specific game requested or found, finding any available game for player {}.", GameWebSocketHandler.playerId(channel));
            // Default to finding any game of the first type in rotation, which findOrCreateGame handles.
            gameToJoin = findOrCreateGame(GAME_ROTATION.get(0).type());
        }

        // 4. Join the determined game
        joinGame(channel, gameToJoin);
    }

    public void spectateGame(Channel channel, String gameIdStr) {
        long gameId = Long.parseLong(gameIdStr);
        AbstractGameStateManager game = activeGames.get(gameId); // Assuming you have a map of active games

        if (game != null) {
            game.addSpectator(channel);
            // Associate the game and a spectator flag with the channel for cleanup on disconnect
            channel.attr(GameWebSocketHandler.GAME_STATE_MANAGER_KEY).set(game);
            channel.attr(GameWebSocketHandler.IS_SPECTATOR_KEY).set(true);
            log.info("Channel {} is now spectating game {}", channel.id().asShortText(), gameId);
        } else {
            log.warn("Spectator tried to join non-existent game {}", gameId);
            playerDisconnected(); // Decrement the count since the connection will be closed
            channel.close();
        }
    }

    private Class<? extends AbstractGameStateManager> findGameTypeClass(String gameTypeStr) {
        for (GameMode mode : GAME_ROTATION) {
            if (mode.type().getSimpleName().equalsIgnoreCase(gameTypeStr)) {
                return mode.type();
            }
        }
        return null;
    }

    public void joinGame(Channel ctx, AbstractGameStateManager game) {
        // Add the player to that specific game instance
        Long playerId = GameWebSocketHandler.playerId(ctx);
        Player player = game.addPlayer(playerId, ctx);
        log.info("Player {} connected and joined game {}", playerId, game.getGameId());

        // Associate the channel with its game and player ID for future lookups
        ctx.attr(GAME_STATE_MANAGER_KEY).set(game);
        ctx.attr(PLAYER_ID_KEY).set(playerId);

        // Send welcome message
        WelcomeMessage welcomeMessage = new WelcomeMessage(player.getId(), player.getTeam(), game.getGameId());
        ctx.writeAndFlush(Jackson.msgPackFrame(welcomeMessage));

        game.sendGameEvent(GameEvent.info(String.format("Joining: %s (%d)!", game.gameType(), game.getGameId())));
    }

    // Finds an available game or creates a new one
    public AbstractGameStateManager findOrCreateGame(Class<? extends AbstractGameStateManager> gameType) {
        // First, try to find a game with an open slot
        for (AbstractGameStateManager game : activeGames.values()) {
            if (!game.isFull() && gameType.isInstance(game)) {
                return game;
            }
        }

        // If no games are available, create a new one
        for (GameMode gameMode : GAME_ROTATION) {
            if (gameMode.type() == gameType) {
                AbstractGameStateManager newGame = gameMode.builder().get();
                log.info("No available games. Creating new game with ID: {}", newGame.getGameId());
                newGame.startGameLoop(); // Each game has its own loop
                activeGames.put(newGame.getGameId(), newGame);
                return newGame;
            }
        }
        throw new IllegalArgumentException("unsupported game type: " + gameType.getSimpleName());
    }

    public void removeGame(Long gameId) {
        AbstractGameStateManager game = activeGames.remove(gameId);
        if (game != null) {
            game.shutdown(); // Method to stop the game loop
            log.info("Removed and shut down game {}", gameId);
        }
    }

    private void cleanupEmptyGames() {
        log.debug("Running cleanup task for empty games...");
        List<Long> gamesToRemove = new ArrayList<>();

        // First, identify all games that have no human players
        for (Map.Entry<Long, AbstractGameStateManager> entry : activeGames.entrySet()) {
            if (!entry.getValue().hasHumanPlayers()) {
                gamesToRemove.add(entry.getKey());
            }
        }

        // Then, remove them to avoid concurrent modification issues
        for (Long gameId : gamesToRemove) {
            log.info("Game {} has no human players. Removing from lobby.", gameId);
            removeGame(gameId);
        }
    }

    public boolean tryAcceptNewPlayer() {
        // Atomically check and increment if below the limit
        int currentCount = globalPlayerCount.get();
        if (currentCount >= MAX_GLOBAL_PLAYERS) {
            return false; // Server is full
        }
        // Attempt to increment, but double-check in case of a race condition
        if (globalPlayerCount.compareAndSet(currentCount, currentCount + 1)) {
            return true; // Success
        }
        // If compareAndSet failed, another thread beat us. Retry or fail.
        // For simplicity, we can just re-check the condition.
        return globalPlayerCount.get() < MAX_GLOBAL_PLAYERS;
    }

    /**
     * Decrements the global player count when a player disconnects.
     */
    public void playerDisconnected() {
        globalPlayerCount.decrementAndGet();
    }

    public int getGlobalPlayerCount() {
        return globalPlayerCount.get();
    }
}
