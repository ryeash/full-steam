package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.Jackson;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WelcomeMessage;

import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.systems.WeaponSystem;
import com.fullsteam.systems.PhysicsEngine;
import com.fullsteam.systems.VehicleManager;
import com.fullsteam.systems.FieldEffectSystem;
import com.fullsteam.systems.TurretSystem;
import com.fullsteam.systems.PlayerManager;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.fullsteam.CollisionUtils.checkObstacleOverlap;
import static com.fullsteam.Config.AFK_TIMEOUT_MS;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.ID_COUNTER;
import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.MAX_SPECTATORS_PER_GAME;

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

import static com.fullsteam.Config.VEHICLE_ACTION_DEBOUNCE_MS;

public abstract class AbstractGameStateManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final GameLobby gameLobby;
    protected final Long gameId = ID_COUNTER.incrementAndGet();
    protected final GameEntities entities;
    protected final WeaponSystem weaponSystem;
    protected final PhysicsEngine physicsEngine;
    protected final VehicleManager vehicleManager;
    protected final FieldEffectSystem fieldEffectSystem;
    protected final TurretSystem turretSystem;
    protected final PlayerManager playerManager;
    protected boolean isRoundOver = false;
    protected ScheduledFuture<?> gameLoopHook;
    protected long lastGameStateUpdate = System.currentTimeMillis();

    public AbstractGameStateManager(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
        this.entities = new GameEntities(GAME_WIDTH, GAME_HEIGHT, 100, 100);
        this.weaponSystem = new WeaponSystem(
                entities,
                this::applyBulletEffect,
                (victim, shooter) -> killPlayer(victim, shooter)
        );
        this.physicsEngine = new PhysicsEngine(entities);
        this.fieldEffectSystem = new FieldEffectSystem(entities, this::killPlayer);
        this.vehicleManager = new VehicleManager(entities, physicsEngine, weaponSystem, fieldEffectSystem, this::sendGameEvent);
        this.turretSystem = new TurretSystem(entities, weaponSystem, fieldEffectSystem, this::sendGameEvent);
        this.playerManager = new PlayerManager(entities, physicsEngine, weaponSystem, vehicleManager, fieldEffectSystem,
                this::sendGameEvent, this::killPlayer, this::playerWelcomeMessage, this::buildAIStrategy, 
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
        log.info("Spectator {} joined game {}", channel.id().asShortText(), gameId);
    }

    public void removeSpectator(Channel channel) {
        entities.getPlayerChannels().remove(channel);
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
        sendGameState();
    }

    public void acceptPlayerInput(Long playerId, PlayerInput input) {
        playerManager.acceptPlayerInput(playerId, input);
    }

    public void handlePlayerInput(Long playerId, PlayerInput input, long delta) {
        Player player = entities.getPlayer(playerId);
        if (player == null) {
            return;
        }
        if (player.isDead()) {
            return;
        }

        // Update the player's aim direction from the input
        player.setMouseX(input.getMouseX());
        player.setMouseY(input.getMouseY());

        // Handle movement (only if player is not in a vehicle)
        Vehicle vehicle = vehicleManager.getPlayerVehicle(playerId);
        if (vehicle == null) {
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
        } else {
            // Player is in a vehicle - delegate to VehicleManager
            vehicleManager.handlePlayerVehicleInput(playerId, input, delta);
        }

        // Handle vehicle enter/exit with debounce
        if (input.isAction2()) {
            long currentTime = System.currentTimeMillis();
            Long lastActionTime = entities.getLastVehicleActionTime(playerId);

            if (lastActionTime == null || currentTime - lastActionTime >= VEHICLE_ACTION_DEBOUNCE_MS) {
                entities.setLastVehicleActionTime(playerId, currentTime);
                vehicleManager.handleVehicleEnterExit(player);
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

            if (weapon.getOrdinance() == Weapon.Ordinance.LASER) {
                // For laser weapons, create a laser blast instead of a bullet
                // laser blasts are hit-scan/instantaneous, so no speed or range decay
                Vector2D start = new Vector2D(finalX, finalY);
                Vector2D end = start.add(new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle)).multiply(weapon.getBulletRange()));
                LaserBlast laserBlast = new LaserBlast(
                        start,
                        end,
                        player.getId(),
                        player.getTeam(),
                        weapon.getBulletDamage() * player.getDamageMultiplier(),
                        System.currentTimeMillis() + 100);
                applyLaser(laserBlast);
                entities.getLaserBlasts().add(laserBlast);
            } else {
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
                entities.getBullets().add(bullet);
            }
        }
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
            playerManager.checkAfkPlayers();
            playerManager.checkAndRespawnPlayers();
            playerManager.updatePowerUps();
            playerManager.updatePlayers(delta);
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



    protected void updateVehicles(long delta) {
        vehicleManager.updateVehicles(delta);
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
                player.setDead(false); // Ensure they are alive
                setValidSpawnPosition(player); // Move them to a spawn point
                player.restoreSpeed();
                player.setVisionObscured(false);
                if (!(player instanceof AIPlayer)) {
                    entities.getPlayerChannel(player.getId())
                            .writeAndFlush(Jackson.msgFrame(playerWelcomeMessage(player)));
                }
            }
            entities.getPlayerChannels().values().forEach(channel ->
                    channel.writeAndFlush(Jackson.msgFrame(new WelcomeMessage(-1, 0, gameId, entities.getObstacles()))));

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

    protected void updatePlayers(long delta) {
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
                player.setDamageMultiplier(Config.POWER_UP_DAMAGE_BOOST_MULTIPLIER);
            }

            // Let the AI make its decisions first, then apply movement
            if (player instanceof AIPlayer ai) {
                GameState gameState = playerGameState(player, null, true);
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

    protected void resetDamageMultiplier(Player player) {
        player.setDamageMultiplier(1.0);
    }

    protected void updateBullets(long delta) {
        entities.getBullets().removeIf(bullet -> {
            // Store the previous position for line-segment collision checks
            Vector2D oldPos = new Vector2D(bullet.getX(), bullet.getY());
            bullet.update(delta);
            Vector2D newPos = new Vector2D(bullet.getX(), bullet.getY());

            // Check bullet-player collisions using the line segment
            Set<Targetable> nearby = entities.getTargetGrid().getNearby(oldPos, newPos);

            for (Targetable target : nearby) {
                switch (target) {
                    case Player player -> {
                        // Check for collision with an enemy player
                        if (!player.isDead() && player.getTeam() != bullet.getTeam()) {
                            Vector2D playerCenter = player.position();
                            // For the purposes of player collisions, we use a slightly larger radius to account fo the bullet not being a point.
                            if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, playerCenter, PLAYER_RADIUS + 2)) {
                                Player shooter = entities.getPlayer(bullet.getShooterId());

                                // Apply damage and check if it was a kill
                                if (player.takeDamage(bullet.getDamage())) {
                                    killPlayer(player, shooter);
                                }
                                applyBulletDestructionEffect(bullet, player);
                                return true; // Remove bullet on hit
                            }
                        }
                    }
                    case Turret turret -> {
                        if (turret.getTeam() != bullet.getTeam()) {
                            Vector2D position = turret.position();
                            if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, position, turret.getRadius())) {
                                // Apply damage and check if it was a kill
                                turret.takeDamage(bullet.getDamage());
                                applyBulletDestructionEffect(bullet, target);
                                return true; // Remove bullet on hit
                            }
                        }
                    }
                    case Vehicle vehicle -> {
                        if (vehicle.getTeam() != bullet.getTeam()) {
                            if (CollisionUtils.checkLinePolygonCollision(oldPos, newPos, vehicle)) {
                                // Apply damage and check if it was a kill
                                vehicle.takeDamage(bullet.getDamage());
                                applyBulletDestructionEffect(bullet, target);
                                return true; // Remove bullet on hit
                            }
                        }
                    }
                    case Obstacle o -> {
                        if (o instanceof HasLife hasLife && CollisionUtils.checkLinePolygonCollision(oldPos, newPos, o)) {
                            hasLife.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, o);
                            return true;
                        }
                    }
                    case null, default -> throw new UnsupportedOperationException("fix for other targets");
                }
            }

            // Check bullet-obstacle collisions using the line segment
            for (Obstacle obstacle : entities.getObstacles()) {
                if (CollisionUtils.checkLinePolygonCollision(oldPos, newPos, obstacle)) {
                    applyBulletDestructionEffect(bullet, obstacle);
                    return true;
                }
            }

            if (bullet.hasExceededMaxDistance() || bullet.getSpeed() < 10) {
                applyBulletDestructionEffect(bullet, null); // null source indicates distance surpassed
                return true;
            }

            // Last check: Remove bullets that will move out of bounds
            return newPos.x() < 0
                   || newPos.x() > GAME_WIDTH
                   || newPos.y() < 0
                   || newPos.y() > GAME_HEIGHT;
        });
    }

    protected void applyLaser(LaserBlast laserBlast) {
        // Check obstacle collisions using the line segment
        for (Obstacle obstacle : entities.getObstacles()) {
            Vector2D collision = CollisionUtils.findLineObstacleCollision(laserBlast.getStart(), laserBlast.getEnd(), obstacle);
            if (collision != null) {
                double currentDistanceSq = laserBlast.getStart().distanceSquared(laserBlast.getEnd());
                if (laserBlast.getStart().distanceSquared(collision) < currentDistanceSq) {
                    // If the collision point is closer than the end point, shorten the laser blast
                    laserBlast.setEnd(collision);
                }
            }
        }

        // Check player collisions using the line segment
        // Use a bounding box to limit the search area for nearby targets
        Set<Targetable> nearby = entities.getTargetGrid().getNearby(laserBlast.getStart(), laserBlast.getEnd());

        for (Targetable target : nearby) {
            switch (target) {
                case Player player -> {
                    // Check for collision with an enemy player
                    if (!player.isDead() && player.getTeam() != laserBlast.getTeam()) {
                        Vector2D playerCenter = player.position();
                        if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), playerCenter, PLAYER_RADIUS)) {
                            Player shooter = entities.getPlayer(laserBlast.getShooterId());

                            // Apply damage and check if it was a kill
                            if (player.takeDamage(laserBlast.getDamage())) {
                                killPlayer(player, shooter);
                            }
                        }
                    }
                }
                case Turret turret -> {
                    if (turret.getTeam() != laserBlast.getTeam()) {
                        Vector2D position = turret.position();
                        if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), position, turret.getRadius())) {
                            // Apply damage and check if it was a kill
                            turret.takeDamage(laserBlast.getDamage());
                        }
                    }
                }
                case Vehicle vehicle -> {
                    if (vehicle.getTeam() != laserBlast.getTeam()) {
                        if (CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), vehicle)) {
                            // Apply damage and check if it was a kill
                            vehicle.takeDamage(laserBlast.getDamage());
                        }
                    }
                }
                case Obstacle o -> {
                    if (o instanceof HasLife hasLife && CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), o)) {
                        hasLife.takeDamage(laserBlast.getDamage());
                    }
                }
                case null, default -> throw new UnsupportedOperationException("fix for other targets");
            }
        }
    }

    protected void applyBulletDestructionEffect(Bullet bullet, Object destructionSource) {
        bullet.getOnDestructionAction()
                .map(action -> action.apply(bullet, destructionSource))
                .ifPresent(this::applyBulletEffect);
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
            spawnPowerUp(new Vector2D(victim.getX(), victim.getY()));
        }
    }

    private void spawnPowerUp(Vector2D position) {
        PowerUpType type = PowerUpType.values()[ThreadLocalRandom.current().nextInt(PowerUpType.values().length)];
        PowerUp powerUp = new PowerUp(position, type);
        entities.getPowerUps().add(powerUp);
    }

    protected void updatePowerUps() {
        entities.getPowerUps().removeIf(powerUp -> {
            Set<Targetable> nearbyPlayers = entities.getTargetGrid().getNearby(powerUp.getPosition().x() - PLAYER_SIZE, powerUp.getPosition().y() - PLAYER_SIZE, PLAYER_SIZE * 2, PLAYER_SIZE * 2);
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
        for (Player player : entities.getPlayers().values()) {
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
        return physicsEngine.isColliding(player, powerUp);
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

    protected boolean isColliding(Player player, Obstacle obstacle) {
        return physicsEngine.isColliding(player, obstacle);
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



    protected void handleVehicleWeaponFiring(Vehicle vehicle, Long playerId, PlayerInput input) {
        Vehicle.MountedWeapon controlledWeapon = vehicle.getWeaponControlledBy(playerId);
        if (controlledWeapon == null) {
            return;
        }

        if (controlledWeapon.isReloading() && System.currentTimeMillis() >= controlledWeapon.getReloadCompleteTime()) {
            controlledWeapon.finishReload();
        }

        // Handle weapon firing
        if (input.isFire()) {
            if (controlledWeapon.getCurrentAmmo() <= 0) {
                controlledWeapon.startReload();
            }
            if (controlledWeapon.canShoot()) {
                weaponSystem.fireVehicleWeapon(vehicle, controlledWeapon, playerId, input);
            }
        }

        // Handle reloading
        if (input.isReload()) {
            controlledWeapon.startReload();
        }
    }

    protected void fireVehicleWeapon(Vehicle vehicle, Vehicle.MountedWeapon mountedWeapon, Long playerId, PlayerInput input) {
        if (!mountedWeapon.canShoot()) {
            return;
        }

        Weapon weapon = mountedWeapon.getWeapon();
        Player controller = entities.getPlayer(playerId);
        if (controller == null) {
            return;
        }

        // Calculate desired weapon angle based on mouse input
        double desiredAngle;
        double dx = input.getMouseX() - mountedWeapon.position().x();
        double dy = input.getMouseY() - mountedWeapon.position().y();
        desiredAngle = Math.atan2(dy, dx);

        // Apply traverse constraints to get the final weapon angle
        double weaponAngle = mountedWeapon.getConstrainedAngle(desiredAngle, vehicle.getAngle());

        // Fire weapon
        int bulletsToFire = Math.min(weapon.getBulletsPerShot(), mountedWeapon.getCurrentAmmo());

        for (int i = 0; i < bulletsToFire; i++) {
            double spread = ThreadLocalRandom.current().nextGaussian() * (weapon.getBulletSpread() / 6.0);
            double finalAngle = weaponAngle + spread;

            if (weapon.getOrdinance() == Weapon.Ordinance.LASER) {
                // Create laser blast
                Vector2D start = mountedWeapon.position();
                Vector2D end = start.add(new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle)).multiply(weapon.getBulletRange()));
                LaserBlast laserBlast = new LaserBlast(
                        start,
                        end,
                        playerId,
                        controller.getTeam(),
                        weapon.getBulletDamage() * mountedWeapon.getDamageModification(),
                        System.currentTimeMillis() + 100);
                applyLaser(laserBlast);
                entities.getLaserBlasts().add(laserBlast);
            } else {
                // Create bullet
                Bullet bullet = new Bullet(
                        mountedWeapon.position().x(),
                        mountedWeapon.position().y(),
                        Math.cos(finalAngle),
                        Math.sin(finalAngle),
                        playerId,
                        controller.getTeam(),
                        weapon.getBulletDamage() * mountedWeapon.getDamageModification(),
                        weapon.getBulletSpeed(),
                        weapon.getBulletRange(),
                        weapon.getBulletSpeedDecay(),
                        weapon.getOnBulletDestruction());
                entities.getBullets().add(bullet);
            }
        }

        mountedWeapon.shoot();
    }

    protected boolean isColliding(Vehicle vehicle, List<Obstacle> obstacles) {
        return physicsEngine.isColliding(vehicle, obstacles);
    }

    protected void handleVehicleEnterExit(Player player) {
        // Check if player is already in a vehicle
        Vehicle currentVehicle = getPlayerVehicle(player.id());
        if (currentVehicle != null) {
            // Exit vehicle
            if (currentVehicle.exitVehicle(player)) {
                // Place player next to the vehicle
                double exitX = currentVehicle.position().x() + currentVehicle.getBoundingRadius() + Config.PLAYER_RADIUS + 5;
                double exitY = currentVehicle.position().y();

                // Make sure exit position is valid
                player.setX(Math.max(Config.PLAYER_RADIUS, Math.min(Config.GAME_WIDTH - Config.PLAYER_RADIUS, exitX)));
                player.setY(Math.max(Config.PLAYER_RADIUS, Math.min(Config.GAME_HEIGHT - Config.PLAYER_RADIUS, exitY)));

                sendGameEvent(GameEvent.info("Exited " + currentVehicle.getVehicleName(), player.id()));
            }
        } else {
            // Try to enter a nearby vehicle
            Vehicle nearbyVehicle = findNearbyVehicle(player);
            if (nearbyVehicle != null && !nearbyVehicle.isDestroyed()) {
                if (nearbyVehicle.enterVehicle(player)) {
                    sendGameEvent(GameEvent.info("Entered " + nearbyVehicle.getVehicleName(), player.id()));
                } else {
                    sendGameEvent(GameEvent.red("Vehicle is full", player.id()));
                }
            } else {
                sendGameEvent(GameEvent.red("No vehicle nearby", player.id()));
            }
        }
    }

    protected Vehicle getPlayerVehicle(Long playerId) {
        return vehicleManager.getPlayerVehicle(playerId);
    }

    protected Vehicle findNearbyVehicle(Player player) {
        return vehicleManager.findNearbyVehicle(player);
    }

    protected void spawnVehicles() {
        vehicleManager.spawnVehicles();
    }

    protected void spawnVehicle(Vehicle.VehicleType type) {
        vehicleManager.spawnVehicle(type);
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
