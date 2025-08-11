package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.Jackson;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Crate;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Hazard;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PoisonCloud;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WelcomeMessage;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.fullsteam.CollisionUtils.checkObstacleOverlap;
import static com.fullsteam.Config.AFK_TIMEOUT_MS;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.HAZARD_COUNT;
import static com.fullsteam.Config.HAZARD_DAMAGE_FACTOR;
import static com.fullsteam.Config.HAZARD_SLOW_FACTOR;
import static com.fullsteam.Config.ID_COUNTER;
import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.MAX_SPECTATORS_PER_GAME;
import static com.fullsteam.Config.MAX_TURRETS_PER_PLAYER;
import static com.fullsteam.Config.OBSTACLE_COUNT;
import static com.fullsteam.Config.PLAYER_RADIUS;
import static com.fullsteam.Config.PLAYER_SIZE;
import static com.fullsteam.Config.POWER_UP_ARMOR_UP_DURATION;
import static com.fullsteam.Config.POWER_UP_DAMAGE_BOOST_DURATION;
import static com.fullsteam.Config.POWER_UP_HEALTH_RECOVERY;
import static com.fullsteam.Config.POWER_UP_INVISIBILITY_DURATION;
import static com.fullsteam.Config.POWER_UP_SPEED_BOOST_DURATION;
import static com.fullsteam.Config.POWER_UP_SPEED_BOOST_FACTOR;
import static com.fullsteam.Config.RESPAWN_DELAY_MS;
import static com.fullsteam.Config.RESPAWN_IMMUNITY_DURATION;
import static com.fullsteam.Config.ROUND_DURATION_SECONDS;
import static com.fullsteam.Config.SPAWN_HORIZONTAL_PADDING;
import static com.fullsteam.Config.SPAWN_MIDFIELD_BUFFER;
import static com.fullsteam.Config.SPAWN_VERTICAL_PADDING;
import static com.fullsteam.Config.TICK_RATE;
import static com.fullsteam.Config.TURRET_INACCURACY;

public abstract class AbstractGameStateManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final GameLobby gameLobby;
    protected final Long gameId = ID_COUNTER.incrementAndGet();
    protected final Map<Long, Player> players = new ConcurrentHashMap<>(10, 1, 1);
    protected final Map<Long, Channel> playerChannels = new ConcurrentHashMap<>(10, 1, 1);
    protected final Map<Long, PlayerInput> playerInput = new ConcurrentHashMap<>(10, 1, 1);
    protected final List<Channel> spectatorChannels = Collections.synchronizedList(new LinkedList<>());
    protected final List<Bullet> bullets = Collections.synchronizedList(new LinkedList<>());
    protected final List<Explosion> explosions = Collections.synchronizedList(new LinkedList<>());
    protected final List<PoisonCloud> poisonClouds = Collections.synchronizedList(new LinkedList<>());
    protected final List<Turret> turrets = Collections.synchronizedList(new LinkedList<>());
    protected final List<Obstacle> obstacles = Collections.synchronizedList(new LinkedList<>());
    protected final List<Hazard> hazards = Collections.synchronizedList(new LinkedList<>());
    protected final List<PowerUp> powerUps = Collections.synchronizedList(new LinkedList<>());
    protected final SpatialGrid<Targetable> targetGrid;
    protected boolean isRoundOver = false;
    protected ScheduledFuture<?> gameLoopHook;
    protected long lastGameStateUpdate = System.currentTimeMillis();

    public AbstractGameStateManager(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
        this.targetGrid = new SpatialGrid<>(GAME_WIDTH, GAME_HEIGHT, 100, 100);
    }

    public final String gameType() {
        return getClass().getAnnotation(GameName.class).value();
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
            // Private message for one player
            Channel channel = playerChannels.get(gameEvent.playerId());
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(frame.retainedDuplicate());
            }
        } else {
            // Broadcast to all players and spectators
            playerChannels.values().forEach(ch -> ch.writeAndFlush(frame.retainedDuplicate()));
            spectatorChannels.forEach(ch -> ch.writeAndFlush(frame.retainedDuplicate()));
        }
        frame.release();
    }

    public boolean isFull() {
        return players.values().stream().filter(p -> !(p instanceof AIPlayer)).count() >= MAX_PLAYERS_PER_TEAM * 2L;
    }

    public boolean hasHumanPlayers() {
        return !players.values().stream().allMatch(p -> p instanceof AIPlayer);
    }

    public boolean isSpectatorsFull() {
        return spectatorChannels.size() >= MAX_SPECTATORS_PER_GAME;
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
        // Assign player to the team with fewer players to keep things balanced.
        long team1Count = players.values().stream().filter(p -> p.getTeam() == 1).count();
        long team2Count = players.values().stream().filter(p -> p.getTeam() == 2).count();
        int team;
        if (team1Count < MAX_PLAYERS_PER_TEAM && team2Count < MAX_PLAYERS_PER_TEAM) {
            team = ThreadLocalRandom.current().nextBoolean() ? 2 : 1;
        } else {
            team = (team2Count <= team1Count) ? 2 : 1;
        }

        Player player = new Player(playerId, 0, 0, team);
        player.applyArmorUp(POWER_UP_ARMOR_UP_DURATION);
        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined team {} at position ({}, {})", playerId, team, player.getX(), player.getY());
        return player;
    }

    public void addSpectator(Channel channel) {
        spectatorChannels.add(channel);
        log.info("Spectator {} joined game {}", channel.id().asShortText(), gameId);
    }

    public void removeSpectator(Channel channel) {
        spectatorChannels.remove(channel);
        log.info("Spectator {} left game {}", channel.id().asShortText(), gameId);
    }

    /**
     * Adds an AI player to a specific team. Called by the TeamBalancer.
     *
     * @param team The team ID to add the AI player to.
     */
    public AIPlayer addAIPlayer(int team) {
        long playerId = ID_COUNTER.incrementAndGet();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, buildAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        return player;
    }

    protected IAIStrategy buildAIStrategy() {
        return new DeathmatchAIStrategy();
    }

    public void removePlayer(long playerId) {
        Player removed = players.remove(playerId);
        playerChannels.remove(playerId);
        playerInput.remove(playerId);
        log.info("Player {} left the game", Optional.ofNullable(removed)
                .map(Player::getPlayerName)
                .orElse(String.valueOf(playerId)));
        sendGameState();
    }

    public void acceptPlayerInput(Long playerId, PlayerInput input) {
        Player player = players.get(playerId);
        if (player == null) {
            return;
        }
        PlayerInput previousInput = playerInput.put(playerId, input);
        if (!Objects.equals(previousInput, input)) {
            player.setLastInputTime(System.currentTimeMillis());
        }
    }

    public void handlePlayerInput(Long playerId, PlayerInput input) {
        Player player = players.get(playerId);
        if (player == null) {
            return;
        }
        if (player.isDead()) {
            return;
        }

        // Update the player's aim direction from the input
        player.setMouseX(input.getMouseX());
        player.setMouseY(input.getMouseY());

        // Handle movement
        double moveX = input.getMoveX();
        double moveY = input.getMoveY();

        Vector2D moveVector = new Vector2D(moveX, moveY);
        double magnitude = moveVector.magnitude();

        // Sanitize input: clamp magnitude to 1.0 to prevent client-side speed hacks
        if (magnitude > 1.0) {
            moveVector = moveVector.normalize();
            magnitude = 1.0;
        }

        if (magnitude > 0.01) { // Use a small deadzone to avoid micro-movements
            // The magnitude of the input (0.0 to 1.0) scales the player's max speed.
            // This allows for walking vs. running with a gamepad.
            double currentSpeed = player.getSpeed() * magnitude;

            // The moveVector is already normalized if magnitude was > 1.0.
            // If not, we normalize it here to get a pure direction vector.
            Vector2D directionVector = moveVector.normalize();
            Vector2D velocity = directionVector.multiply(currentSpeed);

            player.setVelocity(velocity);
        } else {
            player.setVelocity(Vector2D.ZERO); // No input, so no movement
        }

        // Handle reload input before shooting
        if (input.isReload()) {
            player.startReload();
        }

        // Handle shooting
        if (input.isShooting()) {
            if (player.canShoot()) {
                // Calculate bullet direction based on mouse position
                double dx = input.getMouseX() - player.getX();
                double dy = input.getMouseY() - player.getY();
                double length = Math.sqrt(dx * dx + dy * dy);

                if (length > 0) {
                    double baseAngle = Math.atan2(dy / length, dx / length);
                    fireWeapon(player, baseAngle);
                }
            } else if (player.getCurrentAmmoInMagazine() <= 0 && !player.isReloading()) {
                player.startReload();
            }
        }
    }

    protected void fireWeapon(Player player, double aimAngle) {
        if (!player.canShoot()) {
            return;
        }
        Weapon weapon = player.getWeapon();
        double bulletX = player.getX();
        double bulletY = player.getY();

        // Fire all bullets for this shot (or whatever is left in the magazine)
        int bulletsToFire = Math.min(weapon.getBulletsPerShot(), player.getCurrentAmmoInMagazine());

        for (int i = 0; i < bulletsToFire; i++) {
            // Apply random spread to each bullet individually
            double spread = ThreadLocalRandom.current().nextGaussian() * (weapon.getBulletSpread() / 6.0); // 6.0 is 3 standard deviations on each side
            double finalAngle = aimAngle + spread;

            // For multi-bullet shots (like shotguns), add a slight positional stagger
            // so they don't all originate from the exact same pixel. This creates a more natural "spread".
            double finalX = bulletX;
            double finalY = bulletY;
            if (weapon.getBulletsPerShot() > 1) {
                double staggerRadius = 4.0; // Max offset in pixels
                finalX += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * staggerRadius;
                finalY += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * staggerRadius;
            }

            Bullet bullet = new Bullet(
                    finalX,
                    finalY,
                    Math.cos(finalAngle),
                    Math.sin(finalAngle),
                    player.getId(),
                    player.getTeam(),
                    weapon.getBulletDamage() * player.getDamageMultiplier(),
                    weapon.getBulletSpeed(),
                    weapon.getBulletRange(),
                    weapon.getBulletSpeedDecay(),
                    weapon.getOnBulletDestruction());
            bullets.add(bullet);
        }

        // Trigger cooldown and set aim direction after the shot is fired
        player.shoot();
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
            checkAfkPlayers();
            checkAndRespawnPlayers();
            updatePowerUps();
            updatePlayers(delta);
            updateBullets(delta);
            updateExplosions();
            updatePoisonClouds();
            updateTurrets(delta);
            sendGameState();
        } catch (Throwable t) {
            log.error("error executing game loop", t);
        }
    }


    protected void updateTurrets(long delta) {
        GameState gameState = new GameState(
                players.values().stream().filter(p -> p.getInvisibilityEndTime() > System.currentTimeMillis()).toList(),
                bullets,
                explosions,
                poisonClouds,
                turrets,
                obstacles,
                hazards,
                powerUps,
                System.currentTimeMillis(),
                null // GameInfo is not needed for turret AI
        );

        turrets.removeIf(turret -> {
            boolean inObstacle = obstacles.stream()
                    .anyMatch(o -> CollisionUtils.checkCirclePolygonCollision(turret.position(), turret.getRadius(), o.vertices()));
            if (inObstacle) {
                return true;
            }
            if (turret.getHp() <= 0) {
                explosions.add(new Explosion(turret.getX(),
                        turret.getY(),
                        turret.getOwnerId(),
                        turret.getTeam(),
                        PLAYER_SIZE, // size/radius
                        0, // damage
                        300));
                return true;
            }
            Optional<Turret.ShootAction> shootAction = turret.update(gameState, targetGrid);
            shootAction.ifPresent(action -> fireTurretWeapon(turret, Math.atan2(action.directionY(), action.directionX())));
            return false;
        });
    }

    protected void populateSpatialGrids() {
        targetGrid.clear();
        for (Player player : players.values()) {
            targetGrid.insert(player, player.getX(), player.getY(), PLAYER_SIZE, PLAYER_SIZE);
        }
        for (Turret turret : turrets) {
            targetGrid.insert(turret, turret.getX(), turret.getY(), turret.getRadius() * 2, turret.getRadius() * 2);
        }
    }

    /**
     * Handles the lifecycle of explosions, applying damage and removing them when expired.
     */
    protected void updateExplosions() {
        // First, apply damage for any new explosions that haven't dealt it yet.
        for (Explosion explosion : explosions) {
            if (!explosion.hasDamageBeenApplied()) {
                Vector2D explosionCenter = new Vector2D(explosion.getX(), explosion.getY());
                double radiusSq = explosion.getSize() * explosion.getSize();
                Player shooter = players.get(explosion.getShooterId());

                Set<Targetable> nearbyPlayers = targetGrid.getNearby(explosion.getX() - explosion.getSize(), explosion.getY() - explosion.getSize(), explosion.getSize() * 2, explosion.getSize() * 2);
                for (Targetable t : nearbyPlayers) {
                    if (t instanceof Player p) {
                        if (p.isDead()) {
                            continue;
                        }

                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && p.getTeam() == shooter.getTeam() && !Objects.equals(p.getId(), shooter.getId())) {
                            continue;
                        }

                        if (p.position().distanceSquared(explosionCenter) < radiusSq) {
                            if (p.takeDamage(explosion.getDamage())) {
                                killPlayer(p, shooter);
                            }
                        }
                    } else if (t instanceof Turret turret) {
                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && turret.getTeam() == shooter.getTeam() && !Objects.equals(turret.getId(), shooter.getId())) {
                            continue;
                        }
                        if (turret.position().distanceSquared(explosionCenter) < radiusSq) {
                            turret.takeDamage(explosion.getDamage());
                        }
                    } else if (t instanceof Crate crate) {
                        // TODO: probably a more efficient way to handle circle-rectangle collision
                        if (CollisionUtils.checkCirclePolygonCollision(
                                explosionCenter,
                                explosion.getSize(),
                                crate.getVertices())) {
                            // If the explosion hits a crate, apply damage to it.
                            crate.takeDamage(explosion.getDamage());
                        }
                    } else {
                        throw new UnsupportedOperationException("fix for other targetable things");
                    }
                }
                explosion.markDamageApplied(); // Mark it so damage isn't applied again.
            }
        }

        // Next, remove any explosions that have exceeded their visual duration.
        explosions.removeIf(Explosion::isExpired);
    }

    /**
     * Handles the lifecycle of poison clouds, applying damage over time and removing them when expired.
     */
    protected void updatePoisonClouds() {
        long currentTime = System.currentTimeMillis();
        for (PoisonCloud cloud : poisonClouds) {
            // Damage players inside the cloud, ticking every 500ms
            if (currentTime > cloud.getLastDamageTickTime() + 500) {
                Vector2D cloudCenter = new Vector2D(cloud.getX(), cloud.getY());
                double radiusSq = cloud.getRadius() * cloud.getRadius();
                Player shooter = players.get(cloud.getShooterId());

                Set<Targetable> nearbyPlayers = targetGrid.getNearby(cloud.getX() - cloud.getRadius(), cloud.getY() - cloud.getRadius(), cloud.getRadius() * 2, cloud.getRadius() * 2);
                for (Targetable t : nearbyPlayers) {
                    if (t instanceof Player p) {
                        if (p.isDead()) {
                            continue;
                        }
                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && p.getTeam() == shooter.getTeam() && !Objects.equals(p.getId(), shooter.getId())) {
                            continue;
                        }

                        if (p.position().distanceSquared(cloudCenter) < radiusSq) {
                            if (p.takeDamage(cloud.getDamagePerTick())) {
                                killPlayer(p, shooter);
                            }
                        }
                    } else if (t instanceof Turret turret) {
                        if (shooter != null && turret.getTeam() == shooter.getTeam() && !Objects.equals(turret.getId(), shooter.getId())) {
                            continue;
                        }
                        if (turret.position().distanceSquared(cloudCenter) < radiusSq) {
                            turret.takeDamage(cloud.getDamagePerTick());
                        }
                    } else if (t instanceof Crate crate) {
                        // Crates are immune to poison clouds, but we can handle other targetables if needed.
                        continue;
                    } else {
                        throw new UnsupportedOperationException("fix for other targetables");
                    }
                }
                cloud.setLastDamageTickTime(currentTime);
            }
        }

        // Remove any clouds that have exceeded their duration.
        poisonClouds.removeIf(PoisonCloud::isExpired);
    }

    protected abstract boolean checkEndConditions();

    /**
     * Resets the game state for a new round. This includes scores, player positions, and the timer.
     */
    protected void startNewRound() {
        try {
            isRoundOver = false;
            // Clear transient game objects
            bullets.clear();
            poisonClouds.clear();
            turrets.clear();
            powerUps.clear();

            generateObstacles();
            generateHazards();

            // Reset all players
            for (Player player : players.values()) {
                player.resetStats();
                player.resetHp();
                player.finishReload();
                player.setDead(false); // Ensure they are alive
                setValidSpawnPosition(player); // Move them to a spawn point
                if (!(player instanceof AIPlayer)) {
                    playerChannels.get(player.getId())
                            .writeAndFlush(Jackson.msgFrame(new WelcomeMessage(player.getId(), player.getTeam(), gameId)));
                }
            }
            spectatorChannels.forEach(channel ->
                    channel.writeAndFlush(Jackson.msgFrame(new WelcomeMessage(-1, 0, gameId))));

            log.info("New round started! Round will end in {} seconds.", ROUND_DURATION_SECONDS);
            sendGameState(); // Send an immediate update to reflect the reset
        } catch (Throwable t) {
            log.error("error resetting game state", t);
        }
    }

    /**
     * Checks for players who have not sent input for a while and removes them.
     */
    protected void checkAfkPlayers() {
        long currentTime = System.currentTimeMillis();
        List<Long> afkPlayerIds = new ArrayList<>();

        for (Map.Entry<Long, Player> entry : players.entrySet()) {
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
            Channel channel = playerChannels.get(playerId);
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

    protected void updatePlayers(long delta) {
        GameState gameState = new GameState(
                players.values().stream().filter(p -> p.getInvisibilityEndTime() > System.currentTimeMillis()).toList(),
                bullets,
                explosions,
                poisonClouds,
                turrets,
                obstacles,
                hazards,
                powerUps,
                System.currentTimeMillis(),
                null
        );
        for (Player player : players.values()) {
            double oldX = player.getX();
            double oldY = player.getY();

            // Check for reload completion before any other action
            if (player.isReloading() && System.currentTimeMillis() >= player.getReloadCompleteTime()) {
                player.finishReload();
                log.debug("Player {} finished reloading.", player.getId());
            }

            // Apply movement and environmental effects.
            player.restoreSpeed(); // Start with default speed.
            resetDamageMultiplier(player);

            // Speed boost overrides any slowing effects.
            if (System.currentTimeMillis() < player.getSpeedBoostEndTime()) {
                player.setSpeed(player.getDefaultSpeed() * POWER_UP_SPEED_BOOST_FACTOR);
            }

            if (System.currentTimeMillis() < player.getDamageBoostEndTime()) {
                player.setDamageMultiplier(Config.POWER_UP_DAMAGE_BOOST_MULTIPLIER);
            }

            // Process hazards for damage and (if not boosted) slowing.
            for (Hazard hazard : hazards) {
                if (player.position().distanceSquared(hazard.position()) < hazard.radiusSq()) {
                    switch (hazard.type()) {
                        case SLOW:
                            // Apply slow only if the player is not speed-boosted.
                            if (System.currentTimeMillis() >= player.getSpeedBoostEndTime()) {
                                player.setSpeed(Config.DEFAULT_PLAYER_SPEED * hazard.effectValue());
                            }
                            break;
                        case DAMAGE:
                            if (player.takeDamage(hazard.effectValue())) {
                                killPlayer(player, null);
                            }
                            break;
                    }
                }
            }

            // Let the AI make its decisions first, then apply movement
            if (player instanceof AIPlayer ai) {
                Optional<AIPlayer.ShootAction> shootAction = ai.update(gameState, targetGrid, delta);
                if (shootAction.isPresent()) {
                    if (ai.canShoot()) {
                        AIPlayer.ShootAction action = shootAction.get();
                        double baseAngle = Math.atan2(action.directionY(), action.directionX());
                        fireWeapon(ai, baseAngle);
                    }
                } else if (ai.getCurrentAmmoInMagazine() <= 0 && !ai.isReloading()) {
                    ai.startReload();
                }
            } else {
                // Apply velocity for human players
                PlayerInput input = playerInput.get(player.getId());
                if (input != null) {
                    handlePlayerInput(player.getId(), input);
                }
                player.update(delta);
            }

            // --- Collision Resolution with Obstacles ---
            if (isColliding(player, obstacles)) {
                // Player's new position is invalid. Attempt to slide along the obstacle.
                // This is done by testing movement on each axis independently.
                // First, try moving only on the Y axis.
                player.setX(oldX);
                if (isColliding(player, obstacles)) {
                    // That didn't work, so the Y-move was the problem.
                    // Revert Y and try moving only on the X axis.
                    player.setY(oldY);
                    player.setX(oldX + player.getVelocityX());
                    if (isColliding(player, obstacles)) {
                        // Still colliding, can't move on X either. Revert both.
                        player.setX(oldX);
                    }
                }
            }

            // Keep players within game bounds
            player.setX(Math.max(0, Math.min(GAME_WIDTH - PLAYER_SIZE, player.getX())));
            player.setY(Math.max(0, Math.min(GAME_HEIGHT - PLAYER_SIZE, player.getY())));
        }
    }

    protected void resetDamageMultiplier(Player player) {
        player.setDamageMultiplier(1.0);
    }

    protected void updateBullets(long delta) {
        bullets.removeIf(bullet -> {
            // Store the previous position for line-segment collision checks
            Vector2D oldPos = new Vector2D(bullet.getX(), bullet.getY());
            bullet.update(delta);
            Vector2D newPos = new Vector2D(bullet.getX(), bullet.getY());

            // Remove bullets that are out of bounds or have traveled max distance
            if (newPos.x() < 0
                    || newPos.x() > GAME_WIDTH
                    || newPos.y() < 0
                    || newPos.y() > GAME_HEIGHT) {
                return true;
            }

            // Check bullet-obstacle collisions using the line segment
            for (Obstacle obstacle : obstacles) {
                if (CollisionUtils.checkLinePolygonCollision(oldPos, newPos, obstacle.vertices())) {
                    // When a bullet hits an obstacle, trigger its on-destruction effect.
                    bullet.getOnDestructionAction()
                            .map(action -> action.apply(bullet))
                            .ifPresent(this::applyBulletEffect);
                    return true;
                }
            }

            if (bullet.hasExceededMaxDistance() || bullet.getSpeed() < 10) {
                bullet.getOnDestructionAction()
                        .map(action -> action.apply(bullet))
                        .ifPresent(this::applyBulletEffect);
                return true;
            }

            // Check bullet-player collisions using the line segment
            double sx = Math.min(oldPos.x(), newPos.x());
            double sy = Math.min(oldPos.y(), newPos.y());
            double w = Math.abs(oldPos.x() - newPos.x());
            double h = Math.abs(oldPos.y() - newPos.y());
            Set<Targetable> nearby = targetGrid.getNearby(sx, sy, w, h);

            for (Targetable target : nearby) {
                if (target instanceof Player player) {
                    // Check for collision with an enemy player
                    if (!player.isDead() && player.getTeam() != bullet.getTeam()) {
                        Vector2D playerCenter = player.position();
                        if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, playerCenter, PLAYER_RADIUS)) {
                            Player shooter = players.get(bullet.getShooterId());

                            // Apply damage and check if it was a kill
                            if (player.takeDamage(bullet.getDamage())) {
                                killPlayer(player, shooter);
                            }
                            bullet.getOnDestructionAction()
                                    .map(action -> action.apply(bullet))
                                    .ifPresent(this::applyBulletEffect);
                            return true; // Remove bullet on hit
                        }
                    }
                } else if (target instanceof Turret turret) {
                    if (turret.getTeam() != bullet.getTeam()) {
                        Vector2D position = turret.position();
                        if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, position, turret.getRadius())) {
                            // Apply damage and check if it was a kill
                            turret.takeDamage(bullet.getDamage());
                            bullet.getOnDestructionAction()
                                    .map(action -> action.apply(bullet))
                                    .ifPresent(this::applyBulletEffect);
                            return true; // Remove bullet on hit
                        }
                    }
                } else if (target instanceof Crate crate) {
                    if (!crate.isDestroyed() &&
                            CollisionUtils.checkLineRectangleCollision(
                                    oldPos, newPos,
                                    crate.getX(), crate.getY(),
                                    crate.getSize(), crate.getSize())) {
                        crate.takeDamage(bullet.getDamage());
                        bullet.getOnDestructionAction()
                                .map(action -> action.apply(bullet))
                                .ifPresent(this::applyBulletEffect);
                        return true;
                    }
                } else {
                    throw new UnsupportedOperationException("fix for other targets");
                }
            }
            return false;
        });
    }

    protected void applyBulletEffect(BulletEffect bulletEffect) {
        switch (bulletEffect) {
            case Explosion e -> explosions.add(e);
            case PoisonCloud pc -> poisonClouds.add(pc);
            case Turret t -> {
                long ownerId = t.getOwnerId();
                long existingTurrets = turrets.stream()
                        .filter(existing -> existing.getOwnerId() == ownerId)
                        .count();

                if (existingTurrets < MAX_TURRETS_PER_PLAYER) {
                    turrets.add(t);
                } else {
                    // Send a feedback message to the player who tried to place the turret.
                    sendGameEvent(GameEvent.red("Turret limit reached!", ownerId));
                }
            }
            case null, default -> throw new UnsupportedOperationException("unknown effect: " + bulletEffect);
        }
    }

    protected void killPlayer(Player victim, Player shooter) {
        if (victim.isDead()) {
            return; // Prevent scoring on an already dead player
        }

        playerInput.remove(victim.getId());
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
        if (ThreadLocalRandom.current().nextDouble() < 0.25) { // 25% chance to drop a power-up
            spawnPowerUp(new Vector2D(victim.getX(), victim.getY()));
        }
    }

    private void spawnPowerUp(Vector2D position) {
        PowerUpType type = PowerUpType.values()[ThreadLocalRandom.current().nextInt(PowerUpType.values().length)];
        PowerUp powerUp = new PowerUp(position, type);
        powerUps.add(powerUp);
    }

    protected void updatePowerUps() {
        powerUps.removeIf(powerUp -> {
            Set<Targetable> nearbyPlayers = targetGrid.getNearby(powerUp.getPosition().x() - PLAYER_SIZE, powerUp.getPosition().y() - PLAYER_SIZE, PLAYER_SIZE * 2, PLAYER_SIZE * 2);
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

    protected void applyPowerUp(Player player, PowerUp powerUp) {
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

    protected void checkAndRespawnPlayers() {
        long currentTime = System.currentTimeMillis();
        for (Player player : players.values()) {
            if (player.isDead() && currentTime >= player.getRespawnTime()) {
                player.setDead(false);
                player.resetHp();
                player.finishReload();
                player.applyArmorUp(RESPAWN_IMMUNITY_DURATION);
                setValidSpawnPosition(player);
            }
        }
    }

    protected boolean isColliding(Player player, PowerUp powerUp) {
        return player.position().distanceSquared(powerUp.getPosition()) < (PLAYER_SIZE * PLAYER_SIZE); // Using squared distance for efficiency
    }

    /**
     * Builds the game state object with mode-specific data like scores.
     * This must be implemented by concrete game mode managers.
     *
     * @return The fully constructed GameState object.
     */
    protected abstract GameInfo buildGameState();

    protected void sendGameState() {
        GameInfo gameInfo = buildGameState();

        GameState state = new GameState(
                players.values().stream().filter(p -> p.getInvisibilityEndTime() < System.currentTimeMillis()).toList(),
                bullets,
                explosions,
                poisonClouds,
                turrets,
                obstacles.stream().filter(Obstacle::isRendered).toList(),
                hazards,
                // null playerId gets only public events
                powerUps,
                System.currentTimeMillis(),
                gameInfo
        );
        BinaryWebSocketFrame frame = Jackson.msgFrame(state);

        // Send state to all players
        playerChannels.forEach((playerId, channel) -> {
            if (channel.isActive() && channel.isOpen()) {
                channel.writeAndFlush(frame.retainedDuplicate()).addListener(future -> { // retainedDuplicate is crucial
                    if (!future.isSuccess()) {
                        log.error("Failed to send game state to player {}. Closing channel.", playerId, future.cause());
                        channel.close();
                    }
                });
            }
        });

        // Send to all spectators
        if (!spectatorChannels.isEmpty()) {
            for (Channel spectatorChannel : spectatorChannels) {
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

        // Release the original frame after all sends are initiated.
        frame.release();
    }

    protected void generateObstacles() {
        generateObstacles(o -> true);
    }

    protected void generateObstacles(Predicate<Obstacle> checkValidObstacle) {
        obstacles.clear();
        int MAX_RETRIES = 100; // To prevent infinite loops if density is too high

        for (int i = 0; i < OBSTACLE_COUNT / 2; i++) {
            int retries = 0;
            while (retries < MAX_RETRIES) {
                Obstacle candidate = Obstacle.createRandomPolygonObstacle();
                Obstacle clone = candidate.create180Clone();

                boolean isValid = checkValidObstacle.test(candidate) && checkValidObstacle.test(clone);

                boolean overlaps = obstacles.stream().anyMatch(existing ->
                        checkObstacleOverlap(candidate, existing, 40) || checkObstacleOverlap(clone, existing, 40));

                // Also check if the candidate and its clone overlap each other
                if (!overlaps && checkObstacleOverlap(candidate, clone, 40)) {
                    overlaps = true;
                }

                if (isValid && !overlaps) {
                    obstacles.add(candidate);
                    obstacles.add(clone);
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

            // First, check if the spawn point is inside an obstacle.
            if (isColliding(player, obstacles)) {
                invalidPosition = true;
                continue; // Try a new position
            }

            // Next, check if the spawn point is inside a damage hazard.
            for (Hazard hazard : hazards) {
                if (hazard.type() == Hazard.Type.DAMAGE) {
                    // Check if the player's center is inside the hazard's radius.
                    // This is a simplified check, but consistent with how hazards affect players during the game.
                    if (player.position().distanceSquared(hazard.position()) < hazard.radiusSq()) {
                        invalidPosition = true;
                        break; // Exit the for loop and try a new position
                    }
                }
            }
        } while (invalidPosition);
    }

    // --- Collision Detection Methods ---

    protected boolean isColliding(Player player, List<Obstacle> checkObstacles) {
        for (Obstacle obstacle : checkObstacles) {
            if (isColliding(player, obstacle)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isColliding(Player player, Obstacle obstacle) {
        // --- Broad Phase Check ---
        // First, do a quick check using bounding circles to see if the objects are even close.
        double combinedRadius = PLAYER_RADIUS + obstacle.getBoundingRadius();
        double distanceSq = player.position().distanceSquared(obstacle.getCenter());

        // If the distance between centers is greater than their combined radii, they can't be colliding.
        if (distanceSq > combinedRadius * combinedRadius) {
            return false;
        }

        // --- Narrow Phase Check ---
        // The broad phase passed, so now we do the expensive, precise check.
        return CollisionUtils.checkCirclePolygonCollision(player.position(), PLAYER_RADIUS, obstacle.vertices());
    }

    /**
     * Handles a request from a player to change their weapon.
     *
     * @param playerId The ID of the player making the request.
     * @param request  The weapon change request details.
     */
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        Player player = players.get(playerId);
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
            long otherTeamCount = players.values()
                    .stream()
                    .filter(p -> !(p instanceof AIPlayer))
                    .filter(p -> p.getTeam() == otherTeam)
                    .count();

            if (otherTeamCount < MAX_PLAYERS_PER_TEAM) {
                player.setTeam(otherTeam);
                // Kill the player to force a respawn on the new team's side
                killPlayer(player, null);
                playerChannels.get(player.getId())
                        .writeAndFlush(Jackson.msgFrame(new WelcomeMessage(player.getId(), player.getTeam(), gameId)));
                log.info("Player {} switched to team {}", playerId, otherTeam);
            } else {
                sendGameEvent(GameEvent.red("Team %d is full. You cannot switch teams.".formatted(otherTeam), playerId));
            }
        }
        log.info("Player {} reconfigured: name={}, weapon={}", playerId, player.getPlayerName(), player.getWeapon().getName());
    }

    protected void removePlayerTurrets(Player player) {
        turrets.removeIf(t -> t.getOwnerId() == player.id());
    }

    protected void generateHazards() {
        hazards.clear();
        for (Hazard.Type hazardType : Hazard.Type.values()) {
            for (int i = 0; i < HAZARD_COUNT / 2; i++) {
                double radius = ThreadLocalRandom.current().nextDouble(60, 90);
                double x = ThreadLocalRandom.current().nextDouble(150, (Config.GAME_WIDTH / 2.0) - 150);
                double y = ThreadLocalRandom.current().nextDouble(150, Config.GAME_HEIGHT - 150);

                double effectValue = switch (hazardType) {
                    case SLOW -> HAZARD_SLOW_FACTOR;
                    case DAMAGE -> HAZARD_DAMAGE_FACTOR;
                };
                hazards.add(new Hazard(hazardType, new Vector2D(x, y), radius, radius * radius, effectValue));
                hazards.add(new Hazard(hazardType, new Vector2D(Config.GAME_WIDTH - x, Config.GAME_HEIGHT - y), radius, radius * radius, effectValue));
            }
        }
    }

    public void shutdown() {
        spectatorChannels.forEach(Channel::close);
        if (gameLoopHook != null) {
            gameLoopHook.cancel(true);
        }
    }

    public int getPlayerCount() {
        return (int) players.values().stream().filter(p -> !(p instanceof AIPlayer)).count();
    }

    public int getMaxPlayers() {
        return MAX_PLAYERS_PER_TEAM * 2;
    }

    protected void fireTurretWeapon(Turret turret, double aimAngle) {
        Weapon weapon = turret.getWeapon();
        Player owner = players.get(turret.getOwnerId());
        if (owner == null) {
            return;
        }

        for (int i = 0; i < weapon.getBulletsPerShot(); i++) {
            double spread = ThreadLocalRandom.current().nextGaussian() * (weapon.getBulletSpread() / 6.0);
            double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * TURRET_INACCURACY;
            double finalAngle = aimAngle + spread + inaccuracy;

            Bullet bullet = new Bullet(
                    turret.getX(),
                    turret.getY(),
                    Math.cos(finalAngle),
                    Math.sin(finalAngle),
                    turret.getOwnerId(),
                    owner.getTeam(),
                    weapon.getBulletDamage(),
                    weapon.getBulletSpeed(),
                    weapon.getBulletRange(),
                    weapon.getBulletSpeedDecay(),
                    weapon.getOnBulletDestruction());
            bullets.add(bullet);
        }
        turret.shoot();
    }
}
