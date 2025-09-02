package com.fullsteam.model;

import com.fullsteam.SpatialGrid;
import com.fullsteam.model.ai.AIPlayer;
import io.micronaut.websocket.WebSocketSession;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Encapsulates all game entities and collections in a single container.
 * This simplifies method signatures between systems and follows the Parameter Object pattern.
 */
public class GameEntities {

    private final long gameId;

    // Player-related collections
    private final Map<Long, PlayerSession> playerSessions = new ConcurrentHashMap<>(10, 1, 1);
    private final List<WebSocketSession> spectatorChannels = Collections.synchronizedList(new LinkedList<>());

    // Game object collections
    private final List<Bullet> bullets = Collections.synchronizedList(new LinkedList<>());
    private final List<LaserBlast> laserBlasts = Collections.synchronizedList(new LinkedList<>());
    private final List<FieldEffect> fieldEffects = Collections.synchronizedList(new LinkedList<>());
    private final List<Vehicle> vehicles = Collections.synchronizedList(new LinkedList<>());
    private final List<Obstacle> obstacles = Collections.synchronizedList(new LinkedList<>());
    private final List<PowerUp> powerUps = Collections.synchronizedList(new LinkedList<>());

    // Spatial indexing
    private final SpatialGrid<Targetable> targetGrid;

    public GameEntities(long gameId, int gameWidth, int gameHeight, int gridCellWidth, int gridCellHeight) {
        this.gameId = gameId;
        this.targetGrid = new SpatialGrid<>(gameWidth, gameHeight, gridCellWidth, gridCellHeight);
    }

    public long getGameId() {
        return gameId;
    }

    public Map<Long, PlayerSession> getPlayerSessions() {
        return playerSessions;
    }

    public Collection<Player> getPlayers() {
        return playerSessions.values()
                .stream()
                .map(PlayerSession::getPlayer)
                .sorted(Comparator.comparing(Player::getKills).reversed())
                .toList();
    }

    // === Player-related getters ===

    public Stream<WebSocketSession> getPlayerChannels() {
        return playerSessions.values()
                .stream()
                .map(PlayerSession::getSession)
                .filter(Objects::nonNull);
    }

    public List<WebSocketSession> getSpectatorChannels() {
        return spectatorChannels;
    }

    // === Game object getters ===
    public List<Bullet> getBullets() {
        return bullets;
    }

    public List<LaserBlast> getLaserBlasts() {
        return laserBlasts;
    }

    public List<FieldEffect> getFieldEffects() {
        return fieldEffects;
    }

    public List<Vehicle> getVehicles() {
        return vehicles;
    }

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public List<PowerUp> getPowerUps() {
        return powerUps;
    }

    public SpatialGrid<Targetable> getTargetGrid() {
        return targetGrid;
    }

    // === Convenience methods for common operations ===

    /**
     * Clears all transient game objects (bullets, effects, etc.) but keeps players and obstacles
     */
    public void clearTransientObjects() {
        bullets.clear();
        fieldEffects.clear();
        powerUps.clear();
        laserBlasts.clear();
    }

    /**
     * Gets a player by ID, returns null if not found
     */
    public Player getPlayer(Long playerId) {
        PlayerSession playerSession = playerSessions.get(playerId);
        if (playerSession != null) {
            return playerSession.getPlayer();
        }
        return null;
    }

    /**
     * Gets a player channel by ID, returns null if not found
     */
    public WebSocketSession getPlayerChannel(Long playerId) {
        PlayerSession playerSession = playerSessions.get(playerId);
        if (playerSession != null) {
            return playerSession.getSession();
        }
        return null;
    }

    /**
     * Gets player input by ID, returns null if not found
     */
    public PlayerInput getPlayerInput(Long playerId) {
        PlayerSession playerSession = playerSessions.get(playerId);
        if (playerSession != null) {
            return playerSession.getInput();
        }
        return null;
    }

    /**
     * Adds a player and their channel
     */
    public void addPlayer(PlayerSession player) {
        if (player != null) {
            playerSessions.put(player.getPlayerId(), player);
        }
    }

    /**
     * Removes a player and all associated data
     */
    public Player removePlayer(Long playerId) {
        PlayerSession remove = playerSessions.remove(playerId);
        return remove != null ? remove.getPlayer() : null;
    }

    /**
     * Removes player input for a given player
     */
    public void removePlayerInput(Long playerId) {
        PlayerSession playerSession = playerSessions.get(playerId);
        if (playerSession != null) {
            playerSession.setInput(null);
        }
    }

    /**
     * Gets the last vehicle action time for a player
     */
    public Long getLastVehicleActionTime(Long playerId) {
        PlayerSession playerSession = playerSessions.get(playerId);
        if (playerSession != null) {
            return playerSession.getLastVehicleActionTime();
        }
        return null;
    }

    /**
     * Sets the last vehicle action time for a player
     */
    public void setLastVehicleActionTime(Long playerId, Long time) {
        PlayerSession playerSession = playerSessions.get(playerId);
        if (playerSession != null) {
            playerSession.setLastVehicleActionTime(time);
        }
    }

    /**
     * Gets the count of human players (non-AI)
     */
    public int getHumanPlayerCount() {
        return (int) playerSessions.values()
                .stream()
                .filter(ps -> ps.getPlayer() != null && !(ps.getPlayer() instanceof AIPlayer))
                .count();
    }

    /**
     * Gets the count of players on a specific team
     */
    public long getTeamPlayerCount(int team) {
        return (int) playerSessions.values()
                .stream()
                .filter(ps -> ps.getPlayer() != null && ps.getPlayer().getTeam() == team)
                .count();
    }
}
