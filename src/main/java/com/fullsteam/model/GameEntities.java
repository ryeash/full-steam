package com.fullsteam.model;

import com.fullsteam.SpatialGrid;
import io.netty.channel.Channel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.fullsteam.Config.PLAYER_RADIUS;
import static com.fullsteam.Config.PLAYER_SIZE;

/**
 * Encapsulates all game entities and collections in a single container.
 * This simplifies method signatures between systems and follows the Parameter Object pattern.
 */
public class GameEntities {

    // Player-related collections
    private final Map<Long, Player> players = new ConcurrentHashMap<>(10, 1, 1);
    private final Map<Long, Channel> playerChannels = new ConcurrentHashMap<>(10, 1, 1);
    private final Map<Long, PlayerInput> playerInput = new ConcurrentHashMap<>(10, 1, 1);
    private final Map<Long, Long> lastVehicleActionTime = new ConcurrentHashMap<>(10, 1, 1);
    private final List<Channel> spectatorChannels = Collections.synchronizedList(new LinkedList<>());

    // Game object collections
    private final List<Bullet> bullets = Collections.synchronizedList(new LinkedList<>());
    private final List<LaserBlast> laserBlasts = Collections.synchronizedList(new LinkedList<>());
    private final List<FieldEffect> fieldEffects = Collections.synchronizedList(new LinkedList<>());
    private final List<Turret> turrets = Collections.synchronizedList(new LinkedList<>());
    private final List<Vehicle> vehicles = Collections.synchronizedList(new LinkedList<>());
    private final List<Obstacle> obstacles = Collections.synchronizedList(new LinkedList<>());
    private final List<PowerUp> powerUps = Collections.synchronizedList(new LinkedList<>());

    // Spatial indexing
    private final SpatialGrid<Targetable> targetGrid;

    public GameEntities(int gameWidth, int gameHeight, int gridCellWidth, int gridCellHeight) {
        this.targetGrid = new SpatialGrid<>(gameWidth, gameHeight, gridCellWidth, gridCellHeight);
    }

    // === Player-related getters ===
    public Map<Long, Player> getPlayers() {
        return players;
    }

    public Map<Long, Channel> getPlayerChannels() {
        return playerChannels;
    }

    public Map<Long, PlayerInput> getPlayerInput() {
        return playerInput;
    }

    public Map<Long, Long> getLastVehicleActionTime() {
        return lastVehicleActionTime;
    }

    public List<Channel> getSpectatorChannels() {
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

    public List<Turret> getTurrets() {
        return turrets;
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

    public void populateSpatialGrids() {
        targetGrid.clear();
        for (Player player : players.values()) {
            if (player.getVehicleId() != null) {
                continue;
            }
            targetGrid.insert(player, player.getX() - PLAYER_RADIUS, player.getY() - PLAYER_RADIUS, PLAYER_SIZE, PLAYER_SIZE);
        }
        for (Turret turret : turrets) {
            double size = turret.getRadius() * 2;
            targetGrid.insert(turret, turret.getX() - turret.getRadius(), turret.getY() - turret.getRadius(), size, size);
        }
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.isDestroyed()) {
                targetGrid.insertPolygon(vehicle, vehicle.vertices());
            }
        }
    }

    // === Convenience methods for common operations ===

    /**
     * Clears all transient game objects (bullets, effects, etc.) but keeps players and obstacles
     */
    public void clearTransientObjects() {
        bullets.clear();
        fieldEffects.clear();
        turrets.clear();
        powerUps.clear();
        laserBlasts.clear();
    }

    /**
     * Clears all game objects for a complete reset
     */
    public void clearAll() {
        clearTransientObjects();
        players.clear();
        playerChannels.clear();
        playerInput.clear();
        lastVehicleActionTime.clear();
        spectatorChannels.clear();
        vehicles.clear();
        obstacles.clear();
        targetGrid.clear();
    }

    /**
     * Gets a player by ID, returns null if not found
     */
    public Player getPlayer(Long playerId) {
        return players.get(playerId);
    }

    /**
     * Gets a player channel by ID, returns null if not found
     */
    public Channel getPlayerChannel(Long playerId) {
        return playerChannels.get(playerId);
    }

    /**
     * Gets player input by ID, returns null if not found
     */
    public PlayerInput getPlayerInput(Long playerId) {
        return playerInput.get(playerId);
    }

    /**
     * Adds a player and their channel
     */
    public void addPlayer(Player player, Channel channel) {
        players.put(player.id(), player);
        if (channel != null) {
            playerChannels.put(player.id(), channel);
        }
    }

    /**
     * Removes a player and all associated data
     */
    public Player removePlayer(Long playerId) {
        playerChannels.remove(playerId);
        playerInput.remove(playerId);
        lastVehicleActionTime.remove(playerId);
        return players.remove(playerId);
    }

    /**
     * Sets player input for a given player
     */
    public PlayerInput setPlayerInput(Long playerId, PlayerInput input) {
        return playerInput.put(playerId, input);
    }

    /**
     * Removes player input for a given player
     */
    public void removePlayerInput(Long playerId) {
        playerInput.remove(playerId);
    }

    /**
     * Gets the last vehicle action time for a player
     */
    public Long getLastVehicleActionTime(Long playerId) {
        return lastVehicleActionTime.get(playerId);
    }

    /**
     * Sets the last vehicle action time for a player
     */
    public void setLastVehicleActionTime(Long playerId, Long time) {
        lastVehicleActionTime.put(playerId, time);
    }

    /**
     * Gets the count of human players (non-AI)
     */
    public int getHumanPlayerCount() {
        return (int) players.values().stream()
                .filter(p -> !(p instanceof com.fullsteam.model.ai.AIPlayer))
                .count();
    }

    /**
     * Gets the count of players on a specific team
     */
    public long getTeamPlayerCount(int team) {
        return players.values().stream()
                .filter(p -> p.getTeam() == team)
                .count();
    }

    /**
     * Gets the count of human players on a specific team
     */
    public long getHumanTeamPlayerCount(int team) {
        return players.values().stream()
                .filter(p -> !(p instanceof com.fullsteam.model.ai.AIPlayer))
                .filter(p -> p.getTeam() == team)
                .count();
    }
}
