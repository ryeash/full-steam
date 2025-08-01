package com.fullsteam;

import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.games.CaptureTheFlagManager;
import com.fullsteam.games.EliminationManager;
import com.fullsteam.games.JuggernautManager;
import com.fullsteam.games.KingOfTheHillManager;
import com.fullsteam.games.OddballManager;
import com.fullsteam.games.TeamDeathmatchManager;
import com.fullsteam.games.ZombieDefenseManager;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.WelcomeMessage;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.fullsteam.Config.CLEANUP_INTERVAL_SECONDS;
import static com.fullsteam.Config.MAX_GLOBAL_PLAYERS;
import static com.fullsteam.GameWebSocketHandler.GAME_STATE_MANAGER_KEY;
import static com.fullsteam.GameWebSocketHandler.PLAYER_ID_KEY;

public class GameLobby {
    private static final Logger logger = LoggerFactory.getLogger(GameLobby.class);

    private final AtomicInteger globalPlayerCount = new AtomicInteger(0);
    private final Map<Long, AbstractGameStateManager> activeGames = new ConcurrentHashMap<>();
    private final ScheduledExecutorService lobbyMaintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
    private final List<GameMode> GAME_ROTATION = new ArrayList<>();

    record GameMode(Class<? extends AbstractGameStateManager> type, Supplier<AbstractGameStateManager> builder) {
    }

    public GameLobby() {
        lobbyMaintenanceExecutor.scheduleAtFixedRate(this::cleanupEmptyGames, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        logger.info("Lobby maintenance task scheduled to run every {} seconds.", CLEANUP_INTERVAL_SECONDS);
        addGameMode(TeamDeathmatchManager.class, () -> new TeamDeathmatchManager(this));
        addGameMode(CaptureTheFlagManager.class, () -> new CaptureTheFlagManager(this));
        addGameMode(JuggernautManager.class, () -> new JuggernautManager(this));
        addGameMode(ZombieDefenseManager.class, () -> new ZombieDefenseManager(this));
        addGameMode(OddballManager.class, () -> new OddballManager(this));
        addGameMode(EliminationManager.class, () -> new EliminationManager(this));
        addGameMode(KingOfTheHillManager.class, () -> new KingOfTheHillManager(this));
    }

    public void addGameMode(Class<? extends AbstractGameStateManager> type, Supplier<AbstractGameStateManager> builder) {
        GAME_ROTATION.add(new GameMode(type, builder));
    }

    public void joinGame(Channel ctx) {
        AbstractGameStateManager game = findOrCreateGame(GAME_ROTATION.get(0).type);
        joinGame(ctx, game);
    }

    public void joinGame(Channel ctx, AbstractGameStateManager game) {
        // Add the player to that specific game instance
        String playerId = GameWebSocketHandler.playerId(ctx);
        Player player = game.addPlayer(playerId, ctx);
        logger.info("Player {} connected and joined game {}", playerId, game.getGameId());

        // Associate the channel with its game and player ID for future lookups
        ctx.attr(GAME_STATE_MANAGER_KEY).set(game);
        ctx.attr(PLAYER_ID_KEY).set(playerId);

        // Send welcome message
        WelcomeMessage welcomeMessage = new WelcomeMessage(player.getId(), player.getTeam(), game.getGameId());
        ctx.writeAndFlush(new TextWebSocketFrame(Jackson.writeValueAsString(welcomeMessage)));

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
                logger.info("No available games. Creating new game with ID: {}", newGame.getGameId());
                newGame.startGameLoop(); // Each game has its own loop
                activeGames.put(newGame.getGameId(), newGame);
                return newGame;
            }
        }
        throw new IllegalArgumentException("unsupported game type: " + gameType.getSimpleName());
    }

    /**
     * Moves a player from their previous game to the next one in the rotation.
     *
     * @param player       The player to move.
     * @param ctx          The player's channel.
     * @param previousGame The game the player is leaving.
     */
    public void joinNext(Player player, Channel ctx, AbstractGameStateManager previousGame) {
        // 1. Remove player from the old game.
        previousGame.removePlayer(player.getId());

        // 2. Find the index of the current game mode in the rotation.
        int currentIndex = -1;
        for (int i = 0; i < GAME_ROTATION.size(); i++) {
            if (GAME_ROTATION.get(i).type().equals(previousGame.getClass())) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            // This case should ideally not happen if all game modes are registered.
            // As a fallback, join the first game mode in the rotation.
            logger.warn("Could not find previous game mode {} in rotation. Defaulting to the first game mode.", previousGame.getClass().getSimpleName());
            if (!GAME_ROTATION.isEmpty()) {
                Class<? extends AbstractGameStateManager> nextGameType = GAME_ROTATION.get(0).type();
                joinGame(ctx, findOrCreateGame(nextGameType));
            } else {
                logger.error("GAME_ROTATION is empty! Cannot join next game.");
            }
            return;
        }

        // 3. Calculate the index of the next game mode, wrapping around if necessary.
        int nextIndex = (currentIndex + 1) % GAME_ROTATION.size();

        // 4. Get the next game mode's type.
        Class<? extends AbstractGameStateManager> nextGameType = GAME_ROTATION.get(nextIndex).type();
        logger.info("Player {} is rotating from {} to {}.", player.getId(), previousGame.getClass().getSimpleName(), nextGameType.getSimpleName());

        // 5. Find or create a game of the next type and have the player join it.
        joinGame(ctx, findOrCreateGame(nextGameType));
    }

    public void removeGame(Long gameId) {
        AbstractGameStateManager game = activeGames.remove(gameId);
        if (game != null) {
            game.shutdown(); // Method to stop the game loop
            logger.info("Removed and shut down game {}", gameId);
        }
    }

    private void cleanupEmptyGames() {
        logger.debug("Running cleanup task for empty games...");
        List<Long> gamesToRemove = new ArrayList<>();

        // First, identify all games that have no human players
        for (Map.Entry<Long, AbstractGameStateManager> entry : activeGames.entrySet()) {
            if (!entry.getValue().hasHumanPlayers()) {
                gamesToRemove.add(entry.getKey());
            }
        }

        // Then, remove them to avoid concurrent modification issues
        for (Long gameId : gamesToRemove) {
            logger.info("Game {} has no human players. Removing from lobby.", gameId);
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

    public void shutdown() {
        logger.info("Shutting down GameLobby and all active games...");
        lobbyMaintenanceExecutor.shutdownNow();
    }

    public int getGlobalPlayerCount() {
        return globalPlayerCount.get();
    }
}