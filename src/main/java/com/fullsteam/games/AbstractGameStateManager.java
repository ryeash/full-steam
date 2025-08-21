package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.Jackson;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.Mine;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PoisonCloud;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
import com.fullsteam.model.SlowField;
import com.fullsteam.model.SmokeCloud;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WelcomeMessage;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.vehicles.FixedCannon;
import com.fullsteam.model.vehicles.Jeep;
import com.fullsteam.model.vehicles.Mech;
import com.fullsteam.model.vehicles.Tank;
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
import static com.fullsteam.Config.VEHICLE_ACTION_DEBOUNCE_MS;

public abstract class AbstractGameStateManager {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final GameLobby gameLobby;
    protected final Long gameId = ID_COUNTER.incrementAndGet();
    protected final Map<Long, Player> players = new ConcurrentHashMap<>(10, 1, 1);
    protected final Map<Long, Channel> playerChannels = new ConcurrentHashMap<>(10, 1, 1);
    protected final Map<Long, PlayerInput> playerInput = new ConcurrentHashMap<>(10, 1, 1);
    protected final Map<Long, Long> lastVehicleActionTime = new ConcurrentHashMap<>(10, 1, 1);
    protected final List<Channel> spectatorChannels = Collections.synchronizedList(new LinkedList<>());
    protected final List<Bullet> bullets = Collections.synchronizedList(new LinkedList<>());
    protected final List<LaserBlast> laserBlasts = Collections.synchronizedList(new LinkedList<>());
    protected final List<FieldEffect> fieldEffects = Collections.synchronizedList(new LinkedList<>());
    protected final List<Turret> turrets = Collections.synchronizedList(new LinkedList<>());
    protected final List<Vehicle> vehicles = Collections.synchronizedList(new LinkedList<>());
    protected final List<Obstacle> obstacles = Collections.synchronizedList(new LinkedList<>());
    protected final List<PowerUp> powerUps = Collections.synchronizedList(new LinkedList<>());
    protected final SpatialGrid<Targetable> targetGrid;
    protected boolean isRoundOver = false;
    protected ScheduledFuture<?> gameLoopHook;
    protected long lastGameStateUpdate = System.currentTimeMillis();

    public AbstractGameStateManager(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
        this.targetGrid = new SpatialGrid<>(GAME_WIDTH, GAME_HEIGHT, 100, 100);
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
            Channel channel = playerChannels.get(gameEvent.playerId());
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(frame.retainedDuplicate());
            }
        } else {
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
        return addPlayer(playerId, channel, team);
    }

    protected Player addPlayer(long playerId, Channel channel, int team) {
        Player player = new Player(playerId, 0, 0, team);
        player.applyArmorUp(POWER_UP_ARMOR_UP_DURATION);
        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);

        // Send welcome message
        WelcomeMessage welcomeMessage = playerWelcomeMessage(player);
        channel.writeAndFlush(Jackson.msgFrame(welcomeMessage));
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
        lastVehicleActionTime.remove(playerId);
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

    public void handlePlayerInput(Long playerId, PlayerInput input, long delta) {
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

        // Handle movement (only if player is not in a vehicle)
        Vehicle vehicle = getPlayerVehicle(playerId);
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
                        fireWeapon(player, baseAngle);
                    }
                } else if (player.getCurrentAmmoInMagazine() <= 0 && !player.isReloading()) {
                    player.startReload();
                }
            }
        } else {
            // Player is in a vehicle, don't apply normal movement
            player.setVelocity(Vector2D.ZERO);
            player.setX(vehicle.position().x());
            player.setY(vehicle.position().y());
            if (vehicle.getDriverId() == player.id()) {
                vehicle.handleDriverInput(input, delta);
                if (!vehicle.isDestroyed()) {
                    // Update vehicle physics
                    vehicle.update(delta);
                    // Handle driver input
                    if (Objects.equals(vehicle.getDriverId(), playerId)) {
                        vehicle.handleDriverInput(input, delta);
                        // Handle vehicle weapon firing for driver-controlled weapons
                        handleVehicleWeaponFiring(vehicle, vehicle.getDriverId(), input);
                    }
                    // Handle passenger weapon firing
                    for (Long passengerId : vehicle.getPassengerIds()) {
                        handleVehicleWeaponFiring(vehicle, passengerId, input);
                    }
                    // Check collision with obstacles
                    // Simple collision response - stop the vehicle
                    if (isColliding(vehicle, obstacles)) {
                        vehicle.setVelocityX(0);
                        vehicle.setVelocityY(0);
                    }
                }
            }
            handleVehicleWeaponFiring(vehicle, playerId, input);
        }

        // Handle vehicle enter/exit with debounce
        if (input.isAction2()) {
            long currentTime = System.currentTimeMillis();
            Long lastActionTime = lastVehicleActionTime.get(playerId);

            if (lastActionTime == null || currentTime - lastActionTime >= VEHICLE_ACTION_DEBOUNCE_MS) {
                lastVehicleActionTime.put(playerId, currentTime);
                handleVehicleEnterExit(player);
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
                laserBlasts.add(laserBlast);
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
                bullets.add(bullet);
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
            checkAfkPlayers();
            checkAndRespawnPlayers();
            updatePowerUps();
            updatePlayers(delta);
            updateBullets(delta);
            updateLaserBlasts(delta);
            updateTurrets(delta);
            updateVehicles(delta);
            sendGameState();
        } catch (Throwable t) {
            log.error("error executing game loop", t);
        }
    }

    protected void updateTurrets(long delta) {
        GameState gameState = new GameState(
                players.values().stream().filter(p -> p.getInvisibilityEndTime() < System.currentTimeMillis()).toList(),
                bullets,
                laserBlasts,
                fieldEffects,
                turrets,
                vehicles,
                obstacles,
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
                fieldEffects.add(new Explosion(
                        turret.getX(),
                        turret.getY(),
                        turret.getOwnerId(),
                        turret.getTeam(),
                        PLAYER_SIZE, // size/radius
                        0, // damage
                        300));
                return true;
            }
            return false;
        });
        for (Turret turret : turrets) {
            turret.update(gameState, targetGrid)
                    .ifPresent(action -> fireTurretWeapon(turret, Math.atan2(action.directionY(), action.directionX())));
        }
    }

    protected void updateVehicles(long delta) {
        // Remove destroyed vehicles
        vehicles.removeIf(vehicle -> {
            if (vehicle.isDestroyed()) {
                // Create explosion when vehicle is destroyed
                fieldEffects.add(new Explosion(
                        vehicle.position().x(),
                        vehicle.position().y(),
                        0, // No owner for vehicle explosions
                        0, // No team for vehicle explosions
                        vehicle.getBoundingRadius(), // explosion size based on vehicle size
                        0, // No damage from explosion effect itself
                        500)); // Duration
                return true;
            }
            return false;
        });
    }

    protected void populateSpatialGrids() {
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

    private void updateFieldEffects(long delta) {
        for (FieldEffect fieldEffect : List.copyOf(fieldEffects)) {
            switch (fieldEffect) {
                case Explosion explosion -> updateExplosion(explosion);
                case PoisonCloud poisonCloud -> updatePoisonClouds(poisonCloud);
                case SlowField slowField -> updateSlowField(slowField);
                case SmokeCloud smokeCloud -> updateSmokeField(smokeCloud);
                case Mine mine -> updateMineField(mine);
                case null, default ->
                        throw new UnsupportedOperationException("unsupported field effect type: " + fieldEffect.getClass().getSimpleName());
            }
        }
        // Next, remove any effects that have exceeded their duration.
        fieldEffects.removeIf(FieldEffect::isExpired);
    }

    /**
     * Handles the lifecycle of explosions, applying damage and removing them when expired.
     */
    protected void updateExplosion(Explosion explosion) {
        // apply damage for any new explosions that haven't dealt it yet.
        if (!explosion.hasDamageBeenApplied()) {
            Vector2D explosionCenter = new Vector2D(explosion.getX(), explosion.getY());
            Player shooter = players.get(explosion.getShooterId());

            Set<Targetable> nearbyPlayers = targetGrid.getNearby(explosion.position(), explosion.getRadius());
            for (Targetable t : nearbyPlayers) {
                switch (t) {
                    case Player p -> {
                        if (p.isDead()) {
                            continue;
                        }

                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && p.getTeam() == shooter.getTeam() && !Objects.equals(p.getId(), shooter.getId())) {
                            continue;
                        }

                        if (p.position().distanceSquared(explosionCenter) < explosion.getRadiusSquared()) {
                            if (p.takeDamage(explosion.getDamage())) {
                                killPlayer(p, shooter);
                            }
                        }
                    }
                    case Turret turret -> {
                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && turret.getTeam() == shooter.getTeam() && !Objects.equals(turret.getId(), shooter.getId())) {
                            continue;
                        }
                        if (turret.position().distanceSquared(explosionCenter) < explosion.getRadiusSquared()) {
                            turret.takeDamage(explosion.getDamage());
                        }
                    }
                    case Vehicle vehicle -> {
                        if (vehicle.getDriverId() == null || shooter != null && vehicle.getTeam() == shooter.getTeam() && !Objects.equals(vehicle.getId(), shooter.getId())) {
                            continue;
                        }
                        if (vehicle.position().distanceSquared(explosionCenter) < explosion.getRadiusSquared()) {
                            vehicle.takeDamage(explosion.getDamage());
                        }
                    }
                    case Obstacle o -> {
                        if (o instanceof HasLife hasLife && CollisionUtils.checkCirclePolygonCollision(
                                explosionCenter,
                                explosion.getRadius(),
                                o.getVertices())) {
                            // If the explosion hits a crate, apply damage to it.
                            hasLife.takeDamage(explosion.getDamage());
                        }
                    }
                    case null, default -> throw new UnsupportedOperationException("fix for other targetable things");
                }
            }
            explosion.markDamageApplied(); // Mark it so damage isn't applied again.
        }
    }

    /**
     * Handles the lifecycle of poison clouds, applying damage over time and removing them when expired.
     */
    protected void updatePoisonClouds(PoisonCloud cloud) {
        long currentTime = System.currentTimeMillis();
        // Damage players inside the cloud, ticking every 500ms
        if (currentTime > cloud.getLastDamageTickTime() + 500) {
            Vector2D cloudCenter = cloud.position();
            double radiusSq = cloud.getRadiusSquared();
            Player shooter = players.get(cloud.getShooterId());

            Set<Targetable> nearbyPlayers = targetGrid.getNearby(cloud.position(), cloud.getRadius());
            for (Targetable t : nearbyPlayers) {
                switch (t) {
                    case Player p -> {
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
                    }
                    case Turret turret -> {
                        if (shooter != null && turret.getTeam() == shooter.getTeam() && !Objects.equals(turret.getId(), shooter.getId())) {
                            continue;
                        }
                        if (turret.position().distanceSquared(cloudCenter) < radiusSq) {
                            turret.takeDamage(cloud.getDamagePerTick());
                        }
                    }
                    case null, default -> {
                        // poison doesn't apply to anything else
                    }
                }
            }
            cloud.setLastDamageTickTime(currentTime);
        }
    }

    protected void updateSlowField(SlowField slowField) {
        Set<Targetable> nearbyPlayers = targetGrid.getNearby(slowField.position(), slowField.getRadius());
        for (Targetable t : nearbyPlayers) {
            if (t instanceof Player p && !p.isDead() && p.getTeam() != slowField.getTeam()) {
                if (p.position().distanceSquared(slowField.position()) < slowField.getRadiusSquared()) {
                    p.setSpeed(p.getDefaultSpeed() * slowField.getSlowFactor());
                }
            }
        }
    }

    protected void updateSmokeField(SmokeCloud smokeCloud) {
        Set<Targetable> nearbyPlayers = targetGrid.getNearby(smokeCloud.position(), smokeCloud.getRadius());
        for (Targetable t : nearbyPlayers) {
            if (t instanceof Player p && !p.isDead()) {
                if (p.position().distanceSquared(smokeCloud.position()) < smokeCloud.getRadiusSquared()) {
                    p.setVisionObscured(true);
                }
            }
        }
    }

    private void updateMineField(Mine mine) {
        Set<Targetable> nearbyPlayers = targetGrid.getNearby(mine.position(), mine.getRadius());
        for (Targetable t : nearbyPlayers) {
            if (t instanceof Player p && !p.isDead() && mine.getTeam() != p.getTeam()) {
                // If the player is within the mine's radius, trigger the explosion
                if (p.position().distanceSquared(mine.position()) < Math.pow(mine.getRadius() + PLAYER_RADIUS, 2)) {
                    // Trigger the explosion effect
                    fieldEffects.add(Mine.mineExplosion(mine));
                    mine.markTriggered();
                }
            }
        }
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
            fieldEffects.clear();
            turrets.clear();
            powerUps.clear();

            generateObstacles();
            spawnVehicles();

            // Reset all players
            for (Player player : players.values()) {
                player.resetStats();
                player.resetHp();
                player.finishReload();
                player.setDead(false); // Ensure they are alive
                setValidSpawnPosition(player); // Move them to a spawn point
                player.restoreSpeed();
                player.setVisionObscured(false);
                if (!(player instanceof AIPlayer)) {
                    playerChannels.get(player.getId())
                            .writeAndFlush(Jackson.msgFrame(playerWelcomeMessage(player)));
                }
            }
            spectatorChannels.forEach(channel ->
                    channel.writeAndFlush(Jackson.msgFrame(new WelcomeMessage(-1, 0, gameId, obstacles))));

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
            player.setVisionObscured(false);
            resetDamageMultiplier(player);

            updateFieldEffects(delta);

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
                    handlePlayerInput(player.getId(), input, delta);
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
            player.setX(CollisionUtils.constrain(player.getX(), PLAYER_RADIUS, GAME_WIDTH - PLAYER_RADIUS));
            player.setY(CollisionUtils.constrain(player.getY(), PLAYER_RADIUS, GAME_HEIGHT - PLAYER_RADIUS));
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

            // Check bullet-player collisions using the line segment
            Set<Targetable> nearby = targetGrid.getNearby(oldPos, newPos);

            for (Targetable target : nearby) {
                switch (target) {
                    case Player player -> {
                        // Check for collision with an enemy player
                        if (!player.isDead() && player.getTeam() != bullet.getTeam()) {
                            Vector2D playerCenter = player.position();
                            // For the purposes of player collisions, we use a slightly larger radius to account fo the bullet not being a point.
                            if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, playerCenter, PLAYER_RADIUS + 2)) {
                                Player shooter = players.get(bullet.getShooterId());

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
            for (Obstacle obstacle : obstacles) {
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

    private void updateLaserBlasts(long delta) {
        laserBlasts.removeIf(LaserBlast::isExpired);
    }

    protected void applyLaser(LaserBlast laserBlast) {
        // Check obstacle collisions using the line segment
        for (Obstacle obstacle : obstacles) {
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
        Set<Targetable> nearby = targetGrid.getNearby(laserBlast.getStart(), laserBlast.getEnd());

        for (Targetable target : nearby) {
            switch (target) {
                case Player player -> {
                    // Check for collision with an enemy player
                    if (!player.isDead() && player.getTeam() != laserBlast.getTeam()) {
                        Vector2D playerCenter = player.position();
                        if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), playerCenter, PLAYER_RADIUS)) {
                            Player shooter = players.get(laserBlast.getShooterId());

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
            fieldEffects.add(fe);
            return;
        }
        switch (bulletEffect) {
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
        if (ThreadLocalRandom.current().nextDouble() < Config.POWERUP_DROP_RATE) {
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
    protected abstract GameInfo buildGameInfo();

    protected void sendGameState() {
        GameInfo gameInfo = buildGameInfo();

        // Send state to all players
        playerChannels.forEach((playerId, channel) -> {
            if (channel.isActive() && channel.isOpen()) {
                Player player = players.get(playerId);
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

            // check if the spawn point is inside an obstacle.
            if (isColliding(player, obstacles)) {
                invalidPosition = true;
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
                        .writeAndFlush(Jackson.msgFrame(playerWelcomeMessage(player)));
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

    protected void handleVehicleWeaponFiring(Vehicle vehicle, Long playerId, PlayerInput input) {
        Vehicle.MountedWeapon controlledWeapon = vehicle.getWeaponControlledBy(playerId);
        if (controlledWeapon == null) {
            return;
        }

        // Handle weapon firing
        if (input.isFire()) {
            if (controlledWeapon.getCurrentAmmo() <= 0) {
                controlledWeapon.startReload();
            }
            if (controlledWeapon.canShoot()) {
                fireVehicleWeapon(vehicle, controlledWeapon, playerId, input);
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
        Player controller = players.get(playerId);
        if (controller == null) {
            return;
        }

        // Calculate weapon position and angle
        double weaponAngle;

        // Different aiming for different vehicle types
        switch (vehicle.getVehicleType()) {
            case MECH:
            case FIXED_CANNON:
                // Mechs and fixed cannons aim towards mouse cursor
                double dx = input.getMouseX() - vehicle.position().x();
                double dy = input.getMouseY() - vehicle.position().y();
                weaponAngle = Math.atan2(dy, dx) + mountedWeapon.getMountAngleOffset();
                break;
            case JEEP:
                // Jeep minigun can rotate freely towards mouse
                if (playerId.equals(vehicle.getDriverId())) {
                    // Driver doesn't control weapons in jeep
                    return;
                } else {
                    // Passenger controls minigun, aims towards mouse
                    double dx2 = input.getMouseX() - vehicle.position().x();
                    double dy2 = input.getMouseY() - vehicle.position().y();
                    weaponAngle = Math.atan2(dy2, dx2);
                }
                break;
            case TANK:
            default:
                // Tank weapons aim relative to vehicle angle
                if (playerId.equals(vehicle.getDriverId())) {
                    // Driver controls main cannon, aims towards mouse
                    double dx3 = input.getMouseX() - vehicle.position().x();
                    double dy3 = input.getMouseY() - vehicle.position().y();
                    weaponAngle = Math.atan2(dy3, dx3);
                } else {
                    // Passengers control fixed-angle machine guns
                    weaponAngle = vehicle.getAngle() + mountedWeapon.getMountAngleOffset();
                }
                break;
        }

        // Fire weapon
        int bulletsToFire = Math.min(weapon.getBulletsPerShot(), mountedWeapon.getCurrentAmmo());

        for (int i = 0; i < bulletsToFire; i++) {
            double spread = ThreadLocalRandom.current().nextGaussian() * (weapon.getBulletSpread() / 6.0);
            double finalAngle = weaponAngle + spread;

            if (weapon.getOrdinance() == Weapon.Ordinance.LASER) {
                // Create laser blast
                Vector2D start = vehicle.position();
                Vector2D end = start.add(new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle)).multiply(weapon.getBulletRange()));
                LaserBlast laserBlast = new LaserBlast(
                        start,
                        end,
                        playerId,
                        controller.getTeam(),
                        weapon.getBulletDamage() * controller.getDamageMultiplier(),
                        System.currentTimeMillis() + 100);
                applyLaser(laserBlast);
                laserBlasts.add(laserBlast);
            } else {
                // Create bullet
                Bullet bullet = new Bullet(
                        vehicle.position().x(),
                        vehicle.position().y(),
                        Math.cos(finalAngle),
                        Math.sin(finalAngle),
                        playerId,
                        controller.getTeam(),
                        weapon.getBulletDamage() * controller.getDamageMultiplier(),
                        weapon.getBulletSpeed(),
                        weapon.getBulletRange(),
                        weapon.getBulletSpeedDecay(),
                        weapon.getOnBulletDestruction());
                bullets.add(bullet);
            }
        }

        mountedWeapon.shoot();
    }

    protected boolean isColliding(Vehicle vehicle, List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (CollisionUtils.checkObstacleOverlap(vehicle, obstacle, 10)) {
                return true;
            }
        }
        return false;
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
        for (Vehicle vehicle : vehicles) {
            if (vehicle.isPlayerInVehicle(playerId)) {
                return vehicle;
            }
        }
        return null;
    }

    protected Vehicle findNearbyVehicle(Player player) {
        double interactionRadius = Config.VEHICLE_INTERACTION_RADIUS;
        for (Vehicle vehicle : vehicles) {
            if (!vehicle.isDestroyed()) {
                double distance = player.position().distance(vehicle.position());
                if (distance <= interactionRadius) {
                    return vehicle;
                }
            }
        }
        return null;
    }

    protected void spawnVehicles() {
        // Clear existing vehicles
        vehicles.clear();

        // Spawn vehicles at strategic locations
        spawnVehicle(Vehicle.VehicleType.TANK);
//        spawnVehicle(Vehicle.VehicleType.MECH, Config.GAME_WIDTH * 0.75, Config.GAME_HEIGHT * 0.25);
        spawnVehicle(Vehicle.VehicleType.JEEP);
        spawnVehicle(Vehicle.VehicleType.FIXED_CANNON);
//        spawnVehicle(Vehicle.VehicleType.JEEP, Config.GAME_WIDTH * 0.5, Config.GAME_HEIGHT * 0.1);
//        spawnVehicle(Vehicle.VehicleType.TANK, Config.GAME_WIDTH * 0.5, Config.GAME_HEIGHT * 0.9);
    }

    protected void spawnVehicle(Vehicle.VehicleType type) {
        // Try to find a valid position
        Vehicle vehicle = createVehicle(type);
        double bestX = Config.GAME_WIDTH / 2.0;
        double bestY = Config.GAME_HEIGHT / 2.0;
        boolean foundValidPosition = false;

        for (int attempts = 0; attempts < 20; attempts++) {
            double x = ThreadLocalRandom.current().nextDouble(50, Config.GAME_WIDTH - 50);
            double y = ThreadLocalRandom.current().nextDouble(50, Config.GAME_HEIGHT - 50);
            double randomAngle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            vehicle.setAngle(randomAngle);
            vehicle.rotate(randomAngle);
            vehicle.setPosition(new Vector2D(x, y));

            boolean isValid = true;
            for (Obstacle obstacle : obstacles) {
                if (CollisionUtils.checkObstacleOverlap(vehicle, obstacle, 40)) {
                    isValid = false;
                    break;
                }
            }

            if (isValid) {
                vehicles.add(vehicle);
                foundValidPosition = true;
                log.info("Successfully spawned {} at ({}, {}) with rotation {}", type, x, y, Math.toDegrees(randomAngle));
                break;
            } else {
                bestX = x;
                bestY = y;
            }
        }

        // Fallback: spawn anyway at the center if no valid position found
        if (!foundValidPosition) {
            // Apply random rotation for visual variety
            double randomAngle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            vehicle.setAngle(randomAngle);
            vehicle.rotate(randomAngle);
            vehicle.setPosition(new Vector2D(bestX, bestY));


            vehicles.add(vehicle);
            log.warn("Could not find valid position for {} after 20 attempts, spawning at ({}, {}) with rotation {} anyway",
                    type, bestX, bestY, Math.toDegrees(randomAngle));
        }
    }

    protected Vehicle createVehicle(Vehicle.VehicleType type) {
        return switch (type) {
            case TANK -> new Tank();
            case MECH -> new Mech();
            case JEEP -> new Jeep();
            case FIXED_CANNON -> new FixedCannon();
        };
    }

    protected WelcomeMessage playerWelcomeMessage(Player player) {
        return new WelcomeMessage(player.getId(), player.getTeam(), gameId, obstacles);
    }

    protected GameState playerGameState(Player player, GameInfo gameInfo, boolean includeAllObstacles) {
        if (player.isVisionObscured()) {
            return new GameState(
                    List.of(player),
                    List.of(),
                    List.of(),
                    fieldEffects,
                    List.of(),
                    List.of(),
                    includeAllObstacles ? obstacles : obstacles.stream().filter(Obstacle::isRendered).toList(),
                    List.of(),
                    System.currentTimeMillis(),
                    gameInfo);
        } else {
            return new GameState(
                    this.players.values()
                            .stream()
                            .filter(p -> p.getId() == player.getId() || p.getInvisibilityEndTime() < System.currentTimeMillis())
                            .toList(),
                    bullets,
                    laserBlasts,
                    fieldEffects,
                    turrets,
                    vehicles,
                    includeAllObstacles ? obstacles : obstacles.stream().filter(Obstacle::isRendered).toList(),
                    powerUps,
                    System.currentTimeMillis(),
                    gameInfo);
        }
    }

    protected GameState spectatorGameState() {
        return new GameState(
                players.values(),
                bullets,
                laserBlasts,
                fieldEffects,
                turrets,
                vehicles,
                obstacles.stream().filter(Obstacle::isRendered).toList(),
                powerUps,
                System.currentTimeMillis(),
                buildGameInfo());
    }
}
