package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.Jackson;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WelcomeMessage;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.systems.FieldEffectSystem;
import com.fullsteam.systems.PhysicsEngine;
import com.fullsteam.systems.PlayerManager;
import com.fullsteam.systems.TurretSystem;
import com.fullsteam.systems.VehicleManager;
import com.fullsteam.systems.WeaponSystem;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.fullsteam.CollisionUtils.checkObstacleOverlap;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.ID_COUNTER;
import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.MAX_SPECTATORS_PER_GAME;
import static com.fullsteam.Config.OBSTACLE_COUNT;
import static com.fullsteam.Config.RESPAWN_DELAY_MS;
import static com.fullsteam.Config.ROUND_DURATION_SECONDS;
import static com.fullsteam.Config.SPAWN_HORIZONTAL_PADDING;
import static com.fullsteam.Config.SPAWN_MIDFIELD_BUFFER;
import static com.fullsteam.Config.SPAWN_VERTICAL_PADDING;
import static com.fullsteam.Config.TICK_RATE;

public abstract class AbstractGameStateManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final GameLobby gameLobby;
    protected final Long gameId = ID_COUNTER.incrementAndGet();
    protected GameEntities entities;
    protected WeaponSystem weaponSystem;
    protected PhysicsEngine physicsEngine;
    protected VehicleManager vehicleManager;
    protected FieldEffectSystem fieldEffectSystem;
    protected TurretSystem turretSystem;
    protected PlayerManager playerManager;
    protected boolean isRoundOver = false;
    protected ScheduledFuture<?> gameLoopHook;
    protected long lastGameStateUpdate = System.currentTimeMillis();

    public AbstractGameStateManager(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
        this.entities = new GameEntities(GAME_WIDTH, GAME_HEIGHT, 100, 100);
        this.weaponSystem = new WeaponSystem(entities, this::applyBulletEffect, this::killPlayer);
        this.physicsEngine = new PhysicsEngine(entities);
        this.fieldEffectSystem = new FieldEffectSystem(entities, this::killPlayer);
        this.vehicleManager = new VehicleManager(entities, physicsEngine, weaponSystem, fieldEffectSystem, this::sendGameEvent);
        this.turretSystem = new TurretSystem(entities, weaponSystem, fieldEffectSystem, this::sendGameEvent);
        this.playerManager = new PlayerManager(
                entities,
                physicsEngine,
                weaponSystem,
                vehicleManager,
                fieldEffectSystem,
                this::sendGameEvent,
                this::killPlayer,
                this::playerWelcomeMessage,
                this::buildAIStrategy,
                this::setValidSpawnPosition);
    }

    public Long getGameId() {
        return gameId;
    }

    public void sendGameEvent(String message, GameEvent.EventType type) {
        sendGameEvent(new GameEvent(message, type, Config.GAME_EVENT_DURATION_MS, null));
    }

    public void sendGameEvent(GameEvent gameEvent) {
        Map<String, Object> message = Map.of(
                "type", "gameEvent",
                "event", gameEvent
        );
        BinaryWebSocketFrame frame = Jackson.msgFrame(message);

        if (gameEvent.playerId() != null) {
            Channel channel = entities.getPlayerChannel(gameEvent.playerId());
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(frame.retainedDuplicate());
            }
        } else {
            entities.getPlayerChannels().values().forEach(ch -> ch.writeAndFlush(frame.retainedDuplicate()));
            entities.getSpectatorChannels().forEach(ch -> ch.writeAndFlush(frame.retainedDuplicate()));
        }
        frame.release();
    }

    public boolean isFull() {
        return entities.getHumanPlayerCount() >= MAX_PLAYERS_PER_TEAM * 2L;
    }

    public boolean hasHumanPlayers() {
        return !entities.getPlayers().values().stream().allMatch(p -> p instanceof AIPlayer);
    }

    public boolean isSpectatorsFull() {
        return entities.getSpectatorChannels().size() >= MAX_SPECTATORS_PER_GAME;
    }

    public void schedule(Runnable runnable, long delayMs) {
        Config.EXECUTOR.schedule(runnable, delayMs, TimeUnit.MILLISECONDS);
    }

    public void startGameLoop() {
        startNewRound();
        this.gameLoopHook = Config.EXECUTOR.scheduleAtFixedRate(() -> {
            long delta = System.currentTimeMillis() - lastGameStateUpdate;
            updateGame(delta);
            lastGameStateUpdate = System.currentTimeMillis();
        }, 0, 1000 / TICK_RATE, TimeUnit.MILLISECONDS);
        log.info("Game loop started at {} FPS", TICK_RATE);
    }

    public Player addPlayer(long playerId, Channel channel) {
        return playerManager.addPlayer(playerId, channel);
    }

    protected Player addPlayer(long playerId, Channel channel, int team) {
        return playerManager.addPlayer(playerId, channel, team);
    }

    public void addSpectator(Channel channel) {
        entities.getSpectatorChannels().add(channel);
    }

    public void removeSpectator(Channel channel) {
        entities.getSpectatorChannels().remove(channel);
        log.info("Spectator {} left game {}", channel.id().asShortText(), gameId);
    }

    /**
     * Adds an AI player to a specific team. Called by the TeamBalancer.
     *
     * @param team The team ID to add the AI player to.
     */
    public AIPlayer addAIPlayer(int team) {
        return playerManager.addAIPlayer(team);
    }

    protected IAIStrategy buildAIStrategy() {
        return new DeathmatchAIStrategy();
    }

    public void removePlayer(long playerId) {
        playerManager.removePlayer(playerId);
    }

    public void acceptPlayerInput(Long playerId, PlayerInput input) {
        playerManager.acceptPlayerInput(playerId, input);
    }

    protected void updateGame(long delta) {
        try {
            populateSpatialGrids();
            if (!isRoundOver) {
                isRoundOver = checkEndConditions();
                if (isRoundOver) {
                    sendGameEvent(GameEvent.blue("Next round starting in %s seconds".formatted(Config.NEXT_ROUND_DELAY_MS / 1000)));
                    schedule(this::startNewRound, Config.NEXT_ROUND_DELAY_MS);
                }
            }
            playerManager.checkAfkPlayers();
            playerManager.checkAndRespawnPlayers();
            playerManager.updatePowerUps();
            updatePlayers(delta);
            weaponSystem.updateBullets(delta);
            weaponSystem.updateLaserBlasts(delta);
            turretSystem.updateTurrets(delta);
            vehicleManager.updateVehicles(delta);
            sendGameState();
        } catch (Throwable t) {
            log.error("error executing game loop", t);
        }
    }

    protected void populateSpatialGrids() {
        physicsEngine.populateSpatialGrids();
    }

    protected abstract boolean checkEndConditions();

    /**
     * Resets the game state for a new round. This includes scores, player positions, and the timer.
     */
    protected void startNewRound() {
        try {
            isRoundOver = false;
            // Clear transient game objects
            entities.clearTransientObjects();

            generateObstacles();
            vehicleManager.spawnVehicles();

            // Reset all players
            for (Player player : entities.getPlayers().values()) {
                player.resetStats();
                player.resetHp();
                player.finishReload();
                player.setVehicleId(null);
                player.setDead(false); // Ensure they are alive
                setValidSpawnPosition(player); // Move them to a spawn point
                player.restoreSpeed();
                player.setVisionObscured(false);
                if (!(player instanceof AIPlayer)) {
                    entities.getPlayerChannel(player.getId())
                            .writeAndFlush(Jackson.msgFrame(playerWelcomeMessage(player)));
                }
            }
            entities.getSpectatorChannels().forEach(channel ->
                    channel.writeAndFlush(Jackson.msgFrame(new WelcomeMessage(-1, 0, gameId, entities.getObstacles()))));

            log.info("New round started! Round will end in {} seconds.", ROUND_DURATION_SECONDS);
            sendGameState(); // Send an immediate update to reflect the reset
        } catch (Throwable t) {
            log.error("error resetting game state", t);
        }
    }

    protected void updatePlayers(long delta) {
        playerManager.updatePlayers(delta);
    }

    protected void applyBulletEffect(BulletEffect bulletEffect) {
        if (bulletEffect instanceof FieldEffect fe) {
            fieldEffectSystem.addFieldEffect(fe);
            return;
        }
        switch (bulletEffect) {
            case Turret turret -> turretSystem.placeTurret(turret);
            case null, default -> throw new UnsupportedOperationException("unknown effect: " + bulletEffect);
        }
    }

    protected void killPlayer(Player victim, Player shooter) {
        if (victim.isDead()) {
            return; // Prevent scoring on an already dead player
        }

        entities.removePlayerInput(victim.getId());
        victim.setDead(true);
        victim.incrementDeaths();
        victim.setRespawnTime(System.currentTimeMillis() + RESPAWN_DELAY_MS);
        victim.setVelocityX(0);
        victim.setVelocityY(0);
        victim.setInvisibilityEndTime(0);
        victim.setDamageMultiplier(1.0);
        removePlayerTurrets(victim);

        if (shooter != null) {
            shooter.incrementKills();
            sendGameEvent(GameEvent.yellow("You were eliminated by %s (%s)".formatted(shooter.getPlayerName(), shooter.getWeapon().getName()), victim.id()));
            sendGameEvent(GameEvent.blue("You eliminated %s".formatted(victim.getPlayerName()), shooter.id()));
        }

        // If an AI player's performance is unbalanced, give it a new random weapon.
        // This helps prevent an AI from getting stuck with a weapon it's ineffective
        // with or dominating too easily with one it's very good with.
        if (victim instanceof AIPlayer) {
            int kills = victim.getKills();
            int deaths = victim.getDeaths();
            // After at least 3 deaths, check if the kill-death difference is significant.
            if (deaths >= 3 && Math.abs(kills - deaths) > 5) {
                Weapon oldWeapon = victim.getWeapon();
                victim.setWeapon(WeaponFactory.getRandomWeapon());
                log.info("{} performance (K/D: {}/{}) triggered a weapon change from {} to {}.", victim.getPlayerName(), kills, deaths, oldWeapon.getName(), victim.getWeapon().getName());
            }
        }
        if (ThreadLocalRandom.current().nextDouble() < Config.POWERUP_DROP_RATE) {
            PowerUpType type = PowerUpType.values()[ThreadLocalRandom.current().nextInt(PowerUpType.values().length)];
            PowerUp powerUp = new PowerUp(new Vector2D(victim.getX(), victim.getY()), type);
            entities.getPowerUps().add(powerUp);
        }
    }

    /**
     * Builds the game state object with mode-specific data like scores.
     * This must be implemented by concrete game mode managers.
     *
     * @return The fully constructed GameState object.
     */
    protected abstract GameInfo buildGameInfo();

    protected void sendGameState() {
        GameInfo gameInfo = buildGameInfo();

        // Send state to all players
        entities.getPlayerChannels().forEach((playerId, channel) -> {
            if (channel.isActive() && channel.isOpen()) {
                Player player = entities.getPlayer(playerId);
                BinaryWebSocketFrame frameToSend = Jackson.msgFrame(playerGameState(player, gameInfo, false));
                channel.writeAndFlush(frameToSend).addListener(future -> { // retainedDuplicate is crucial
                    if (!future.isSuccess()) {
                        log.error("Failed to send game state to player {}. Closing channel.", playerId, future.cause());
                        channel.close();
                    }
                });
            }
        });

        // Send to all spectators
        GameState gameState = spectatorGameState();
        BinaryWebSocketFrame frame = Jackson.msgFrame(gameState);
        if (!entities.getPlayerChannels().isEmpty()) {
            for (Channel spectatorChannel : entities.getSpectatorChannels()) {
                if (spectatorChannel.isActive() && spectatorChannel.isOpen()) {
                    spectatorChannel.writeAndFlush(frame.retainedDuplicate()).addListener(future -> {
                        if (!future.isSuccess()) {
                            log.error("Failed to send game state to spectator {}. Closing channel.", spectatorChannel.id().asShortText(), future.cause());
                            spectatorChannel.close();
                        }
                    });
                }
            }
        }
        frame.release();
    }

    protected void generateObstacles() {
        generateObstacles(o -> true);
    }

    protected void generateObstacles(Predicate<Obstacle> checkValidObstacle) {
        entities.getObstacles().clear();
        int MAX_RETRIES = 100; // To prevent infinite loops if density is too high

        for (int i = 0; i < OBSTACLE_COUNT / 2; i++) {
            int retries = 0;
            while (retries < MAX_RETRIES) {
                Obstacle candidate = Obstacle.createRandomPolygonObstacle();
                Obstacle clone = candidate.create180Clone();

                boolean isValid = checkValidObstacle.test(candidate) && checkValidObstacle.test(clone);

                boolean overlaps = entities.getObstacles().stream().anyMatch(existing ->
                        checkObstacleOverlap(candidate, existing, 40) || checkObstacleOverlap(clone, existing, 40));

                // Also check if the candidate and its clone overlap each other
                if (!overlaps && checkObstacleOverlap(candidate, clone, 40)) {
                    overlaps = true;
                }

                if (isValid && !overlaps) {
                    entities.getObstacles().add(candidate);
                    entities.getObstacles().add(clone);
                    break; // Success, move to the next pair
                }
                retries++;
            }
            if (retries >= MAX_RETRIES) {
                log.warn("Could not place non-overlapping obstacle pair after {} retries. Obstacle density may be too high.", MAX_RETRIES);
            }
        }
    }

    protected void setValidSpawnPosition(Player player) {
        boolean invalidPosition;
        do {
            invalidPosition = false;
            double x;

            // Calculate the available width for spawning on one side of the map.
            final double spawnableWidth = (GAME_WIDTH / 2.0) - SPAWN_HORIZONTAL_PADDING - SPAWN_MIDFIELD_BUFFER;

            // Spawn players on their respective sides of the map
            if (player.getTeam() == 1) {
                // Team 1 spawns on the left half, away from the edge and the center.
                x = SPAWN_HORIZONTAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableWidth;
            } else {
                // Team 2 spawns on the right half, away from the edge and the center.
                double startX = (GAME_WIDTH / 2.0) + SPAWN_MIDFIELD_BUFFER;
                x = startX + ThreadLocalRandom.current().nextDouble() * spawnableWidth;
            }

            // Calculate a random Y position, away from the top and bottom edges.
            final double spawnableHeight = GAME_HEIGHT - (2 * SPAWN_VERTICAL_PADDING);
            double y = SPAWN_VERTICAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableHeight;

            player.setX(x);
            player.setY(y);

            // check if the spawn point is inside an obstacle.
            if (isColliding(player, entities.getObstacles())) {
                invalidPosition = true;
            }
        } while (invalidPosition);
    }

// --- Collision Detection Methods ---

    protected boolean isColliding(Player player, List<Obstacle> checkObstacles) {
        return physicsEngine.isColliding(player, checkObstacles);
    }

    /**
     * Handles a request from a player to change their weapon.
     *
     * @param playerId The ID of the player making the request.
     * @param request  The weapon change request details.
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
            int currentTeam = player.getTeam();
            int otherTeam = (currentTeam == 1) ? 2 : 1;

            // Check if the other team is full
            // only count human players
            long otherTeamCount = entities.getPlayers().values()
                    .stream()
                    .filter(p -> !(p instanceof AIPlayer))
                    .filter(p -> p.getTeam() == otherTeam)
                    .count();

            if (otherTeamCount < MAX_PLAYERS_PER_TEAM) {
                player.setTeam(otherTeam);
                // Kill the player to force a respawn on the new team's side
                killPlayer(player, null);
                entities.getPlayerChannel(player.getId())
                        .writeAndFlush(Jackson.msgFrame(playerWelcomeMessage(player)));
                log.info("Player {} switched to team {}", playerId, otherTeam);
            } else {
                sendGameEvent(GameEvent.red("Team %d is full. You cannot switch teams.".formatted(otherTeam), playerId));
            }
        }
        log.info("Player {} reconfigured: name={}, weapon={}", playerId, player.getPlayerName(), player.getWeapon().getName());
    }

    protected void removePlayerTurrets(Player player) {
        entities.getTurrets().removeIf(t -> t.getOwnerId() == player.id());
    }

    public void shutdown() {
        entities.getPlayerChannels().values().forEach(Channel::close);
        if (gameLoopHook != null) {
            gameLoopHook.cancel(true);
        }
    }

    public int getPlayerCount() {
        return (int) entities.getPlayers().values().stream().filter(p -> !(p instanceof AIPlayer)).count();
    }

    public int getMaxPlayers() {
        return MAX_PLAYERS_PER_TEAM * 2;
    }

    protected WelcomeMessage playerWelcomeMessage(Player player) {
        return new WelcomeMessage(player.getId(), player.getTeam(), gameId, entities.getObstacles());
    }

    protected GameState playerGameState(Player player, GameInfo gameInfo, boolean includeAllObstacles) {
        if (player.isVisionObscured()) {
            return new GameState(
                    List.of(player),
                    List.of(),
                    List.of(),
                    entities.getFieldEffects(),
                    List.of(),
                    List.of(),
                    includeAllObstacles ? entities.getObstacles() : entities.getObstacles().stream().filter(Obstacle::isRendered).toList(),
                    List.of(),
                    System.currentTimeMillis(),
                    gameInfo);
        } else {
            return new GameState(
                    entities.getPlayers().values()
                            .stream()
                            .filter(p -> p.getId() == player.getId() || p.getInvisibilityEndTime() < System.currentTimeMillis())
                            .toList(),
                    entities.getBullets(),
                    entities.getLaserBlasts(),
                    entities.getFieldEffects(),
                    entities.getTurrets(),
                    entities.getVehicles(),
                    includeAllObstacles ? entities.getObstacles() : entities.getObstacles().stream().filter(Obstacle::isRendered).toList(),
                    entities.getPowerUps(),
                    System.currentTimeMillis(),
                    gameInfo);
        }
    }

    protected GameState spectatorGameState() {
        return new GameState(
                entities.getPlayers().values(),
                entities.getBullets(),
                entities.getLaserBlasts(),
                entities.getFieldEffects(),
                entities.getTurrets(),
                entities.getVehicles(),
                entities.getObstacles().stream().filter(Obstacle::isRendered).toList(),
                entities.getPowerUps(),
                System.currentTimeMillis(),
                buildGameInfo());
    }
}
