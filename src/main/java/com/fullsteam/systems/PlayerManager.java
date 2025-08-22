package com.fullsteam.systems;

import com.fullsteam.Config;
import com.fullsteam.Jackson;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WelcomeMessage;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.fullsteam.Config.AFK_TIMEOUT_MS;
import static com.fullsteam.Config.ID_COUNTER;
import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.PLAYER_RADIUS;
import static com.fullsteam.Config.POWER_UP_ARMOR_UP_DURATION;
import static com.fullsteam.Config.POWER_UP_DAMAGE_BOOST_DURATION;
import static com.fullsteam.Config.POWER_UP_DAMAGE_BOOST_MULTIPLIER;
import static com.fullsteam.Config.POWER_UP_HEALTH_RECOVERY;
import static com.fullsteam.Config.POWER_UP_INVISIBILITY_DURATION;
import static com.fullsteam.Config.POWER_UP_SPEED_BOOST_DURATION;
import static com.fullsteam.Config.POWER_UP_SPEED_BOOST_FACTOR;
import static com.fullsteam.Config.RESPAWN_IMMUNITY_DURATION;

/**
 * Handles all player-related functionality including lifecycle management, input handling,
 * movement, power-ups, respawning, and AFK checking. Separated from AbstractGameStateManager
 * to follow Single Responsibility Principle.
 */
public class PlayerManager {

    private static final Logger log = LoggerFactory.getLogger(PlayerManager.class);

    private final GameEntities entities;
    private final PhysicsEngine physicsEngine;
    private final WeaponSystem weaponSystem;
    private final VehicleManager vehicleManager;
    private final FieldEffectSystem fieldEffectSystem;
    private final Consumer<GameEvent> gameEventSender;
    private final BiConsumer<Player, Player> killPlayerHandler;
    private final Function<Player, WelcomeMessage> welcomeMessageBuilder;
    private final Supplier<IAIStrategy> aiStrategyBuilder;
    private final Consumer<Player> setValidSpawnPositionHandler;

    /**
     * Clone constructor for PlayerManager.
     * <p>
     * This creates a shallow copy of the PlayerManager, sharing references to all
     * underlying systems and entities.
     *
     * @param other The PlayerManager to clone.
     */
    public PlayerManager(PlayerManager other) {
        this.entities = other.entities;
        this.physicsEngine = other.physicsEngine;
        this.weaponSystem = other.weaponSystem;
        this.vehicleManager = other.vehicleManager;
        this.fieldEffectSystem = other.fieldEffectSystem;
        this.gameEventSender = other.gameEventSender;
        this.killPlayerHandler = other.killPlayerHandler;
        this.welcomeMessageBuilder = other.welcomeMessageBuilder;
        this.aiStrategyBuilder = other.aiStrategyBuilder;
        this.setValidSpawnPositionHandler = other.setValidSpawnPositionHandler;
    }

    public PlayerManager(GameEntities entities, PhysicsEngine physicsEngine, WeaponSystem weaponSystem,
                         VehicleManager vehicleManager, FieldEffectSystem fieldEffectSystem,
                         Consumer<GameEvent> gameEventSender, BiConsumer<Player, Player> killPlayerHandler,
                         Function<Player, WelcomeMessage> welcomeMessageBuilder, Supplier<IAIStrategy> aiStrategyBuilder,
                         Consumer<Player> setValidSpawnPositionHandler) {
        this.entities = entities;
        this.physicsEngine = physicsEngine;
        this.weaponSystem = weaponSystem;
        this.vehicleManager = vehicleManager;
        this.fieldEffectSystem = fieldEffectSystem;
        this.gameEventSender = gameEventSender;
        this.killPlayerHandler = killPlayerHandler;
        this.welcomeMessageBuilder = welcomeMessageBuilder;
        this.aiStrategyBuilder = aiStrategyBuilder;
        this.setValidSpawnPositionHandler = setValidSpawnPositionHandler;
    }

    /**
     * Updates all players, handles movement, AI decisions, effects, and collisions
     */
    public void updatePlayers(long delta) {
        for (Player player : entities.getPlayers().values()) {
            double oldX = player.getX();
            double oldY = player.getY();

            // Check for reload completion before any other action
            if (player.isReloading() && System.currentTimeMillis() >= player.getReloadCompleteTime()) {
                player.finishReload();
                log.debug("Player {} finished reloading.", player.getId());
            }

            // Apply movement and environmental effects.
            player.restoreSpeed(); // Start with default speed.
            player.setVisionObscured(false);
            resetDamageMultiplier(player);

            fieldEffectSystem.updateFieldEffects(delta);

            // Speed boost overrides any slowing effects.
            if (System.currentTimeMillis() < player.getSpeedBoostEndTime()) {
                player.setSpeed(player.getDefaultSpeed() * POWER_UP_SPEED_BOOST_FACTOR);
            }

            if (System.currentTimeMillis() < player.getDamageBoostEndTime()) {
                player.setDamageMultiplier(POWER_UP_DAMAGE_BOOST_MULTIPLIER);
            }

            // Let the AI make its decisions first, then apply movement
            if (player instanceof AIPlayer ai) {
                updateAIPlayer(ai, delta);
            } else {
                // Apply velocity for human players
                PlayerInput input = entities.getPlayerInput(player.getId());
                if (input != null) {
                    handlePlayerInput(player.getId(), input, delta);
                }
                player.update(delta);
            }

            // --- Collision Resolution with Obstacles ---
            physicsEngine.resolvePlayerObstacleCollisions(player, oldX, oldY);

            // Keep players within game bounds
            physicsEngine.constrainPlayerToBounds(player);
        }
    }

    /**
     * Updates an AI player's decision making and actions
     */
    private void updateAIPlayer(AIPlayer ai, long delta) {
        GameState gameState = createPlayerGameState(ai, null, true);
        Optional<AIPlayer.ShootAction> shootAction = ai.update(gameState, entities.getTargetGrid(), delta);
        if (shootAction.isPresent()) {
            if (ai.canShoot()) {
                AIPlayer.ShootAction action = shootAction.get();
                double baseAngle = Math.atan2(action.directionY(), action.directionX());
                weaponSystem.fireWeapon(ai, baseAngle);
            }
        } else if (ai.getCurrentAmmoInMagazine() <= 0 && !ai.isReloading()) {
            ai.startReload();
        }
    }

    /**
     * Handles player input for movement, shooting, reloading
     */
    public void handlePlayerInput(Long playerId, PlayerInput input, long delta) {
        Player player = entities.getPlayer(playerId);
        if (player == null || player.isDead()) {
            return;
        }

        // Update the player's aim direction from the input
        player.setMouseX(input.getMouseX());
        player.setMouseY(input.getMouseY());

        // Handle movement (only if player is not in a vehicle)
        Vehicle vehicle = vehicleManager.getPlayerVehicle(playerId);
        if (vehicle == null) {
            handlePlayerMovement(player, input);
            handlePlayerShooting(player, input);
        } else {
            // Player is in a vehicle - delegate to VehicleManager
            vehicleManager.handlePlayerVehicleInput(playerId, input, delta);
        }

        // Handle vehicle enter/exit with debounce
        if (input.isAction2()) {
            long currentTime = System.currentTimeMillis();
            Long lastActionTime = entities.getLastVehicleActionTime(playerId);
            if (lastActionTime == null || currentTime - lastActionTime >= Config.VEHICLE_ACTION_DEBOUNCE_MS) {
                entities.setLastVehicleActionTime(playerId, currentTime);
                vehicleManager.handleVehicleEnterExit(player);
            }
        }
    }

    /**
     * Handles player movement input
     */
    private void handlePlayerMovement(Player player, PlayerInput input) {
        double moveX = input.getMoveX();
        double moveY = input.getMoveY();

        Vector2D moveVector = new Vector2D(moveX, moveY);
        double magnitude = moveVector.magnitude();

        // Sanitize input: clamp magnitude to 1.0 to prevent client-side speed hacks
        if (magnitude > 1.0) {
            moveVector = moveVector.normalize();
            magnitude = 1.0;
        }

        if (magnitude > 0.01) {
            double currentSpeed = player.getSpeed() * magnitude;
            Vector2D directionVector = moveVector.normalize();
            Vector2D velocity = directionVector.multiply(currentSpeed);
            player.setVelocity(velocity);
        } else {
            player.setVelocity(Vector2D.ZERO); // No input, so no movement
        }
    }

    /**
     * Handles player shooting and reloading input
     */
    private void handlePlayerShooting(Player player, PlayerInput input) {
        if (input.isReload()) {
            player.startReload();
        }
        if (input.isFire()) {
            if (player.canShoot()) {
                // Calculate bullet direction based on mouse position
                double dx = input.getMouseX() - player.getX();
                double dy = input.getMouseY() - player.getY();
                double length = Math.sqrt(dx * dx + dy * dy);

                if (length > 0) {
                    double baseAngle = Math.atan2(dy / length, dx / length);
                    weaponSystem.fireWeapon(player, baseAngle);
                }
            } else if (player.getCurrentAmmoInMagazine() <= 0 && !player.isReloading()) {
                player.startReload();
            }
        }
    }

    /**
     * Accepts and processes player input
     */
    public void acceptPlayerInput(Long playerId, PlayerInput input) {
        Player player = entities.getPlayer(playerId);
        if (player == null) {
            return;
        }
        PlayerInput previousInput = entities.setPlayerInput(playerId, input);
        if (!Objects.equals(previousInput, input)) {
            player.setLastInputTime(System.currentTimeMillis());
        }
    }

    /**
     * Updates power-ups and handles player collection
     */
    public void updatePowerUps() {
        entities.getPowerUps().removeIf(powerUp -> {
            Set<Targetable> nearbyPlayers = entities.getTargetGrid().getNearby(powerUp.getPosition(), PLAYER_RADIUS);
            for (Targetable target : nearbyPlayers) {
                if (target instanceof Player player) {
                    if (!player.isDead() && isColliding(player, powerUp)) {
                        applyPowerUp(player, powerUp);
                        return true; // Power-up is consumed, remove it
                    }
                }
            }
            return false;
        });
    }

    /**
     * Applies a power-up effect to a player
     */
    private void applyPowerUp(Player player, PowerUp powerUp) {
        switch (powerUp.getType()) {
            case HEALTH_PACK:
                player.takeDamage(-POWER_UP_HEALTH_RECOVERY);
                break;
            case SPEED_BOOST:
                player.applySpeedBoost(POWER_UP_SPEED_BOOST_DURATION);
                break;
            case ARMOR_UP:
                player.applyArmorUp(POWER_UP_ARMOR_UP_DURATION);
                break;
            case DAMAGE_BOOST:
                player.applyDamageBoost(POWER_UP_DAMAGE_BOOST_DURATION);
                break;
            case INVISIBILITY:
                player.setInvisibilityEndTime(System.currentTimeMillis() + POWER_UP_INVISIBILITY_DURATION);
                break;
        }
    }

    /**
     * Checks for AFK players and removes them
     */
    public void checkAfkPlayers() {
        long currentTime = System.currentTimeMillis();
        List<Long> afkPlayerIds = new ArrayList<>();

        for (Map.Entry<Long, Player> entry : entities.getPlayers().entrySet()) {
            Player player = entry.getValue();
            // We don't want to kick AI players
            if (player instanceof AIPlayer) {
                continue;
            }

            if (currentTime - player.getLastInputTime() > AFK_TIMEOUT_MS) {
                afkPlayerIds.add(entry.getKey());
            }
        }

        for (Long playerId : afkPlayerIds) {
            log.info("Player {} is AFK. Removing from game.", playerId);
            Channel channel = entities.getPlayerChannel(playerId);
            if (channel != null) {
                // Closing the channel will trigger the channelInactive event in the
                // GameWebSocketHandler, which will then call removePlayer.
                channel.close();
            } else {
                // If there's no channel, but the player exists, it's a dangling player. Remove it directly.
                removePlayer(playerId);
            }
        }
    }

    /**
     * Checks for dead players and respawns them when ready
     */
    public void checkAndRespawnPlayers() {
        long currentTime = System.currentTimeMillis();
        for (Player player : entities.getPlayers().values()) {
            if (player.isDead()
                && player.getRespawnTime() != -1 // indicates a player's respawn has been disabled
                && currentTime >= player.getRespawnTime()) {
                respawnPlayer(player);
            }
        }
    }

    /**
     * Respawns a dead player
     */
    private void respawnPlayer(Player player) {
        player.setDead(false);
        player.resetHp();
        player.finishReload();
        player.applyArmorUp(RESPAWN_IMMUNITY_DURATION);
        setValidSpawnPositionHandler.accept(player);
    }

    /**
     * Adds a new player to the game with balanced team assignment
     */
    public Player addPlayer(long playerId, Channel channel) {
        // Assign player to the team with fewer players to keep things balanced.
        long team1Count = entities.getTeamPlayerCount(1);
        long team2Count = entities.getTeamPlayerCount(2);
        int team;
        if (team1Count < MAX_PLAYERS_PER_TEAM && team2Count < MAX_PLAYERS_PER_TEAM) {
            team = ThreadLocalRandom.current().nextBoolean() ? 2 : 1;
        } else {
            team = (team2Count <= team1Count) ? 2 : 1;
        }
        return addPlayer(playerId, channel, team);
    }

    /**
     * Adds a new player to a specific team
     */
    public Player addPlayer(long playerId, Channel channel, int team) {
        Player player = new Player(playerId, 0, 0, team);
        player.applyArmorUp(POWER_UP_ARMOR_UP_DURATION);
        setValidSpawnPositionHandler.accept(player);
        entities.addPlayer(player, channel);

        // Send welcome message
        WelcomeMessage welcomeMessage = welcomeMessageBuilder.apply(player);
        channel.writeAndFlush(Jackson.msgFrame(welcomeMessage));
        return player;
    }

    /**
     * Adds an AI player to a specific team
     */
    public AIPlayer addAIPlayer(int team) {
        long playerId = ID_COUNTER.incrementAndGet();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, aiStrategyBuilder.get(), AIArchetype.randomArchetype());
        setValidSpawnPositionHandler.accept(player);
        entities.addPlayer(player, null);
        return player;
    }

    /**
     * Removes a player from the game
     */
    public void removePlayer(long playerId) {
        Player removed = entities.removePlayer(playerId);
        log.info("Player {} left the game", Optional.ofNullable(removed)
                .map(Player::getPlayerName)
                .orElse(String.valueOf(playerId)));
        Optional.ofNullable(removed)
                .map(Player::id)
                .map(vehicleManager::getPlayerVehicle)
                .ifPresent(v -> v.exitVehicle(removed));
    }

    /**
     * Handles player configuration changes (weapon, team, name)
     */
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        Player player = entities.getPlayer(playerId);
        if (player == null) {
            return;
        }

        if (request.getPlayerName() != null && !request.getPlayerName().isEmpty() && Config.ALLOW_NAME_CHANGE) {
            player.setPlayerName(request.getPlayerName());
        }

        if (request.getWeaponName() != null
            && !request.getWeaponName().isEmpty()
            && !request.getWeaponName().equals(player.getWeapon().getName())) {
            Weapon newWeapon = WeaponFactory.getWeapon(request.getWeaponName());
            player.setWeapon(newWeapon);
            removePlayerTurrets(player);
        }

        if (request.isRequestTeamChange()) {
            handleTeamChangeRequest(player);
        }
        log.info("Player {} reconfigured: name={}, weapon={}", playerId, player.getPlayerName(), player.getWeapon().getName());
    }

    /**
     * Handles a player's request to change teams
     */
    private void handleTeamChangeRequest(Player player) {
        int currentTeam = player.getTeam();
        int otherTeam = (currentTeam == 1) ? 2 : 1;

        // Check if the other team is full (only count human players)
        long otherTeamCount = entities.getPlayers().values()
                .stream()
                .filter(p -> !(p instanceof AIPlayer))
                .filter(p -> p.getTeam() == otherTeam)
                .count();

        if (otherTeamCount < MAX_PLAYERS_PER_TEAM) {
            player.setTeam(otherTeam);
            // Kill the player to force a respawn on the new team's side
            killPlayerHandler.accept(player, null);
            entities.getPlayerChannel(player.getId())
                    .writeAndFlush(Jackson.msgFrame(welcomeMessageBuilder.apply(player)));
            log.info("Player {} switched to team {}", player.getId(), otherTeam);
        } else {
            gameEventSender.accept(GameEvent.red("Team %d is full. You cannot switch teams.".formatted(otherTeam), player.getId()));
        }
    }

    /**
     * Removes all turrets owned by a player
     */
    private void removePlayerTurrets(Player player) {
        // This should be handled by TurretSystem, but we need a way to call it
        // For now, we'll access the turrets directly
        entities.getTurrets().removeIf(turret -> turret.getOwnerId() == player.getId());
    }

    /**
     * Resets a player's damage multiplier to default
     */
    protected void resetDamageMultiplier(Player player) {
        player.setDamageMultiplier(1.0);
    }

    /**
     * Checks if a player collides with a power-up
     */
    private boolean isColliding(Player player, PowerUp powerUp) {
        return physicsEngine.isColliding(player, powerUp);
    }

    /**
     * Creates a game state for a specific player (for AI or other purposes)
     */
    private GameState createPlayerGameState(Player player, GameInfo gameInfo, boolean includeAllObstacles) {
        return new GameState(
                entities.getPlayers().values().stream()
                        .filter(p -> p.getInvisibilityEndTime() < System.currentTimeMillis() || Objects.equals(p.getId(), player.getId()))
                        .toList(),
                entities.getBullets(),
                entities.getLaserBlasts(),
                entities.getFieldEffects(),
                entities.getTurrets(),
                entities.getVehicles(),
                includeAllObstacles ? entities.getObstacles() : entities.getObstacles().stream().filter(o -> o.isRendered()).toList(),
                entities.getPowerUps(),
                System.currentTimeMillis(),
                gameInfo);
    }

    /**
     * Gets all players (read-only access)
     */
    public Map<Long, Player> getPlayers() {
        return java.util.Collections.unmodifiableMap(entities.getPlayers());
    }

    /**
     * Gets a specific player by ID
     */
    public Player getPlayer(long playerId) {
        return entities.getPlayer(playerId);
    }

    /**
     * Gets the total number of human players
     */
    public int getHumanPlayerCount() {
        return entities.getHumanPlayerCount();
    }

    /**
     * Gets the number of players on a specific team
     */
    public long getTeamPlayerCount(int team) {
        return entities.getTeamPlayerCount(team);
    }

    /**
     * Checks if the maximum number of players has been reached
     */
    public boolean isPlayerLimitReached() {
        return getHumanPlayerCount() >= MAX_PLAYERS_PER_TEAM * 2;
    }

    /**
     * Gets all AI players
     */
    public List<AIPlayer> getAIPlayers() {
        return entities.getPlayers().values().stream()
                .filter(AIPlayer.class::isInstance)
                .map(AIPlayer.class::cast)
                .toList();
    }

    /**
     * Gets all human players
     */
    public List<Player> getHumanPlayers() {
        return entities.getPlayers().values().stream()
                .filter(p -> !(p instanceof AIPlayer))
                .toList();
    }
}
