package com.fullsteam;

import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.games.BaseDestructionManager;
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
import com.fullsteam.games.PortalManager;
import com.fullsteam.games.ProgressionManager;
import com.fullsteam.games.TeamDeathmatchManager;
import com.fullsteam.games.ZombieDefenseManager;
import com.fullsteam.model.ActiveGame;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

@Singleton
public class GameLobby {
    private static final Logger log = LoggerFactory.getLogger(GameLobby.class);

    private final ApplicationContext ctx;
    private final Semaphore globalPlayerCountSemaphore;
    private final Map<Long, ActiveGame> activeGames = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends AbstractGameStateManager>> gameMap = new LinkedHashMap<>();

    @Inject
    public GameLobby(ApplicationContext ctx) {
        this.ctx = ctx;
        this.globalPlayerCountSemaphore = new Semaphore(Config.MAX_GLOBAL_PLAYERS);

        Config.EXECUTOR.scheduleAtFixedRate(this::cleanupEmptyGames,
                Config.CLEANUP_INTERVAL_SECONDS,
                Config.CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
        log.info("Lobby maintenance task scheduled to run every {} seconds.",
                Config.CLEANUP_INTERVAL_SECONDS);
        gameMap.put("Team Deathmatch", TeamDeathmatchManager.class);
        gameMap.put("Capture The Flag", CaptureTheFlagManager.class);
        gameMap.put("King Of The Hill", KingOfTheHillManager.class);
        gameMap.put("Elimination", EliminationManager.class);
        gameMap.put("Oddball", OddballManager.class);
        gameMap.put("Gun Master", GunMasterManager.class);
        gameMap.put("Juggernaut", JuggernautManager.class);
        gameMap.put("Escort", EscortManager.class);
        gameMap.put("Free For All", FreeForAllManager.class);
        gameMap.put("Lone Wolf", LoneWolfManager.class);
        gameMap.put("Builder", BuilderManager.class);
        gameMap.put("Zombie Defense", ZombieDefenseManager.class);
        gameMap.put("Base Destruction", BaseDestructionManager.class);
        gameMap.put("Portal", PortalManager.class);
        gameMap.put("Progression", ProgressionManager.class);
    }

    public List<ActiveGame> getActiveGames() {
        return new ArrayList<>(activeGames.values());
    }

    public List<String> getGameTypes() {
        return gameMap.keySet()
                .stream()
                .toList();
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

        Class<? extends AbstractGameStateManager> gameClass = gameMap.get(gameType);
        if (gameClass != null) {
            AbstractGameStateManager newGame = ctx.createBean(gameClass);
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
        return Config.MAX_GLOBAL_PLAYERS - globalPlayerCountSemaphore.availablePermits();
    }

    /**
     * Find a game by its ID
     */
    public AbstractGameStateManager findGameById(long gameId) {
        ActiveGame activeGame = activeGames.get(gameId);
        return activeGame != null ? activeGame.getGame() : null;
    }
}
