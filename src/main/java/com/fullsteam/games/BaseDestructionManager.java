package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Base;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.MotorPool;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.ai.UnifiedAIStrategy;
import com.fullsteam.model.gamemodes.BaseDestructionInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_HEALTH;
import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_RADIUS;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.MOTOR_POOL_CONTROL_TIME_MS;
import static com.fullsteam.Config.MOTOR_POOL_RADIUS;
import static com.fullsteam.Config.VEHICLE_REPAIR_RATE;

/**
 * Dual Base Destruction game mode.
 * Both teams have a base to defend and must destroy the enemy base while protecting their own.
 * Teams can capture motor pools to spawn vehicles for attack and defense.
 * First team to destroy the enemy base wins the round.
 */
@Prototype
public class BaseDestructionManager extends AbstractTeamBasedManager {

    private static final Logger log = LoggerFactory.getLogger(BaseDestructionManager.class);

    private Base team1Base;
    private Base team2Base;
    private boolean team1BaseDestroyed = false;
    private boolean team2BaseDestroyed = false;

    private List<MotorPool> motorPools;
    private final Vehicle.VehicleType[] vehicleTypes = new Vehicle.VehicleType[]{
            Vehicle.VehicleType.JEEP,
            Vehicle.VehicleType.DAVINCI,
            Vehicle.VehicleType.MECH,
            Vehicle.VehicleType.TANK,
    };
    private int team1VehicleIndex = 0;
    private int team2VehicleIndex = 0;

    @Inject
    public BaseDestructionManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        initializeBasesAndMotorPools();
    }

    private void initializeBasesAndMotorPools() {
        generateBasePositions();
        initializeMotorPools();
    }

    private void initializeMotorPools() {
        motorPools = new ArrayList<>();
        motorPools.add(new MotorPool(
                new Vector2D(GAME_WIDTH * 0.15, GAME_HEIGHT * 0.85),
                MOTOR_POOL_RADIUS,
                MOTOR_POOL_RADIUS * MOTOR_POOL_RADIUS,
                0,
                false,
                0,
                MOTOR_POOL_CONTROL_TIME_MS,
                1
        ));
        motorPools.add(new MotorPool(
                new Vector2D(GAME_WIDTH * 0.85, GAME_HEIGHT * 0.15),
                MOTOR_POOL_RADIUS,
                MOTOR_POOL_RADIUS * MOTOR_POOL_RADIUS,
                0,
                false,
                0,
                MOTOR_POOL_CONTROL_TIME_MS,
                2
        ));
    }

    @Override
    public PlayerSession addPlayer(long playerId, WebSocketSession channel) {
        // Balance teams equally for symmetric gameplay
        long team1Count = entities.getPlayers().stream().filter(p -> p.getTeam() == 1).count();
        long team2Count = entities.getPlayers().stream().filter(p -> p.getTeam() == 2).count();

        int team;
        if (team1Count < Config.MAX_PLAYERS_PER_TEAM && team2Count < Config.MAX_PLAYERS_PER_TEAM) {
            // Assign to the team with fewer players
            team = (team1Count <= team2Count) ? 1 : 2;
        } else if (team1Count < Config.MAX_PLAYERS_PER_TEAM) {
            team = 1;
        } else if (team2Count < Config.MAX_PLAYERS_PER_TEAM) {
            team = 2;
        } else {
            // Both teams are full, assign randomly
            team = ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        }

        return addPlayer(playerId, channel, team);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();
        team1BaseDestroyed = false;
        team2BaseDestroyed = false;
        vehicleManager.resetVehicles();
        entities.getVehicles().clear();
        motorPools.replaceAll(motorPool -> motorPool.withState(0, false, 0));
        team1VehicleIndex = 0;
        team2VehicleIndex = 0;
        generateBasePositions();
        spawnDefensiveCannons();
        sendGameEvent(GameEvent.yellow("Destroy the opposing team's base while defending your own!"));
        sendGameEvent(GameEvent.info("Capture motor pools to spawn vehicles!"));
    }

    @Override
    protected void updateGame(long delta) {
        applyDeathmatchScoring();
        updateMotorPools();
        handleVehicleRepair();
        updateBases();
        super.updateGame(delta);
    }

    private void updateBases() {
        // Check Team 1 base destruction
        if (team1Base != null && !team1BaseDestroyed && team1Base.isDestroyed()) {
            fieldEffectSystem.addFieldEffect(new Explosion(
                    team1Base.getX(),
                    team1Base.getY(),
                    0,
                    0,
                    team1Base.getRadius(),
                    1000,
                    300));
            team1BaseDestroyed = true;
            sendGameEvent(GameEvent.red("Team 1's base has been destroyed!"));
        }

        // Check Team 2 base destruction
        if (team2Base != null && !team2BaseDestroyed && team2Base.isDestroyed()) {
            fieldEffectSystem.addFieldEffect(new Explosion(
                    team2Base.getX(),
                    team2Base.getY(),
                    0,
                    0,
                    team2Base.getRadius(),
                    1000,
                    300));
            team2BaseDestroyed = true;
            sendGameEvent(GameEvent.red("Team 2's base has been destroyed!"));
        }
    }

    private void updateMotorPools() {
        for (int i = 0; i < motorPools.size(); i++) {
            MotorPool pool = motorPools.get(i);

            // Count players from each team in the motor pool
            int team1Count = 0;
            int team2Count = 0;

            for (Player player : entities.getPlayers()) {
                if (player.isDead()) {
                    continue;
                }

                double distanceSq = pool.position().distanceSquared(new Vector2D(player.getX(), player.getY()));
                if (distanceSq <= pool.radiusSq()) {
                    if (player.getTeam() == 1) {
                        team1Count++;
                    } else if (player.getTeam() == 2) {
                        team2Count++;
                    }
                }
            }

            // Determine control state
            boolean contested = team1Count > 0 && team2Count > 0;
            int controllingTeam = contested ? 0 : (team1Count > 0 ? 1 : (team2Count > 0 ? 2 : 0));

            // Update control timing
            long controlStartTime = pool.controlStartTime();
            if (controllingTeam != pool.controllingTeam() || contested != pool.contested()) {
                // Control state changed, reset timer
                controlStartTime = contested || controllingTeam == 0 ? 0 : System.currentTimeMillis();
            }

            // Update the motor pool state
            MotorPool updatedPool = pool.withState(controllingTeam, contested, controlStartTime);
            motorPools.set(i, updatedPool);

            // Check if control was just completed
            if (pool.isFullyControlled()) {
                spawnVehicleForTeam(controllingTeam, pool.position());
                motorPools.set(i, pool.withState(controllingTeam, false, System.currentTimeMillis()));
            }
        }
    }

    private void spawnVehicleForTeam(int team, Vector2D poolPosition) {
        Vehicle.VehicleType typeToSpawn;
        if (team == 1) {
            typeToSpawn = vehicleTypes[team1VehicleIndex % vehicleTypes.length];
            team1VehicleIndex++;
        } else {
            typeToSpawn = vehicleTypes[team2VehicleIndex % vehicleTypes.length];
            team2VehicleIndex++;
        }

        Vehicle vehicle = vehicleManager.spawnVehicle(typeToSpawn);

        // Position vehicle near the motor pool but not overlapping
        Vector2D spawnPosition = findValidSpawnPosition(poolPosition);
        vehicle.setPosition(spawnPosition);

        sendGameEvent(GameEvent.team(team, "A %s has been deployed from your Motor Pool!".formatted(typeToSpawn.name().toLowerCase().replace('_', ' '))));
        log.info("Spawned {} for team {} at position ({}, {})", typeToSpawn, team, spawnPosition.x(), spawnPosition.y());
    }

    private Vector2D findValidSpawnPosition(Vector2D poolPosition) {
        // Try to find a valid spawn position near the motor pool
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = MOTOR_POOL_RADIUS + 50 + ThreadLocalRandom.current().nextDouble() * 50;

            double x = poolPosition.x() + Math.cos(angle) * distance;
            double y = poolPosition.y() + Math.sin(angle) * distance;

            // Ensure position is within game bounds
            x = Math.max(50, Math.min(GAME_WIDTH - 50, x));
            y = Math.max(50, Math.min(GAME_HEIGHT - 50, y));

            Vector2D candidate = new Vector2D(x, y);

            // Check if position is clear of obstacles
            boolean clear = entities.getObstacles().stream()
                    .noneMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(candidate, 30, obstacle.vertices()));

            if (clear) {
                return candidate;
            }
        }

        // Fallback to motor pool position if no clear spot found
        return poolPosition;
    }

    private void handleVehicleRepair() {
        for (Vehicle vehicle : entities.getVehicles()) {
            if (vehicle.isDestroyed()) continue;

            // Check if vehicle is in its team's motor pool
            MotorPool teamPool = null;
            int vehicleTeam = vehicle.getTeam();

            if (vehicleTeam == 1 && !motorPools.isEmpty()) {
                teamPool = motorPools.getFirst();
            } else if (vehicleTeam == 2 && motorPools.size() > 1) {
                teamPool = motorPools.get(1);
            }

            if (teamPool != null) {
                double distanceSq = teamPool.position().distanceSquared(vehicle.position());
                if (distanceSq <= teamPool.radiusSq()) {
                    // Vehicle is in motor pool, repair it
                    vehicle.heal(VEHICLE_REPAIR_RATE);
                }
            }
        }
    }

    @Override
    protected void populateSpatialGrids() {
        super.populateSpatialGrids();

        // Add both bases to spatial grid if they exist and aren't destroyed
        if (team1Base != null && !team1Base.isDestroyed()) {
            double size = team1Base.getRadius() * 2;
            entities.getTargetGrid().insert(team1Base,
                    team1Base.getX() - team1Base.getRadius(),
                    team1Base.getY() - team1Base.getRadius(),
                    size, size);
        }

        if (team2Base != null && !team2Base.isDestroyed()) {
            double size = team2Base.getRadius() * 2;
            entities.getTargetGrid().insert(team2Base,
                    team2Base.getX() - team2Base.getRadius(),
                    team2Base.getY() - team2Base.getRadius(),
                    size, size);
        }
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        if (team1BaseDestroyed && team2BaseDestroyed) {
            // Both bases destroyed - draw
            if (!sentVictoryMessage) {
                sendVictoryMessage();
            }
            return true;
        } else if (team1BaseDestroyed) {
            // Team 2 wins
            if (!sentVictoryMessage) {
                sendVictoryMessage();
            }
            return true;
        } else if (team2BaseDestroyed) {
            // Team 1 wins
            if (!sentVictoryMessage) {
                sendVictoryMessage();
            }
            return true;
        } else if (roundTimerExpired) {
            // Time expired - both bases survived, it's a draw
            if (!sentVictoryMessage) {
                sendVictoryMessage();
            }
            return true;
        }
        return false;
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new BaseDestructionInfo(
                team1Base,
                team2Base,
                timeLeft,
                team1BaseDestroyed,
                team2BaseDestroyed,
                motorPools,
                team1Score,
                team2Score
        );
    }

    @Override
    protected void generateObstacles() {
        entities.getObstacles().clear();
        generateStaticDefensiveWall();
    }

    private void generateStaticDefensiveWall() {
        // Create a simple static wall with exactly 2 gaps for cannons
        double wallThickness = 50;
        // Wall segment 1 (top)
        // create parallelogram as the upper wall
        {
            Vector2D topLeft = new Vector2D(((double) GAME_WIDTH / 2) - 250, 60);
            Obstacle topWall = new Obstacle(List.of(
                    topLeft,
                    topLeft.add(new Vector2D(wallThickness, 0)),
                    topLeft.add(new Vector2D(wallThickness * 2, 150)),
                    topLeft.add(new Vector2D(wallThickness, 150))
            ));
            entities.getObstacles().add(topWall);
            entities.getObstacles().add(topWall.create180Clone());
        }

        // Wall segment 2 (bottom)
        // another parallelogram
        {
            Vector2D topLeft = new Vector2D(wallThickness + ((double) GAME_WIDTH / 2) - 250, 300);
            Obstacle bottomWall = new Obstacle(List.of(
                    topLeft,
                    topLeft.add(new Vector2D(wallThickness, 0)),
                    topLeft.add(new Vector2D(0, 150)),
                    topLeft.add(new Vector2D(-wallThickness, 150))
            ));
            entities.getObstacles().add(bottomWall);
            entities.getObstacles().add(bottomWall.create180Clone());
        }

        // Wall segment 3 (motor pool cover)
        {
            Vector2D topLeft = new Vector2D(((double) GAME_WIDTH / 2) - 250, 600);

            Obstacle bottomWall = new Obstacle(List.of(
                    topLeft,
                    topLeft.add(new Vector2D(wallThickness, 0)),
                    topLeft.add(new Vector2D(wallThickness, 150)),
                    topLeft.add(new Vector2D(0, 150))
            ));
            entities.getObstacles().add(bottomWall);
            entities.getObstacles().add(bottomWall.create180Clone());
        }
    }

    private void spawnDefensiveCannons() {
        Vehicle team1Cannon1 = vehicleManager.spawnVehicle(Vehicle.VehicleType.FIXED_CANNON);
        team1Cannon1.setPosition(new Vector2D(((double) GAME_WIDTH / 2) - 160, 255));

        Vehicle team2Cannon1 = vehicleManager.spawnVehicle(Vehicle.VehicleType.FIXED_CANNON);
        team2Cannon1.setPosition(new Vector2D(GAME_WIDTH, GAME_HEIGHT).subtract(team1Cannon1.position()));
    }

    private void generateBasePositions() {
        // Team 1 base (top-left corner)
        double team1BaseX = GAME_WIDTH * 0.15; // 20% across the map
        double team1BaseY = GAME_HEIGHT * 0.3; // 20% down the map

        Vector2D team1BasePosition = new Vector2D(team1BaseX, team1BaseY);
        this.team1Base = new Base(
                Config.ID_COUNTER.incrementAndGet(),
                team1BasePosition,
                BASE_DESTRUCTION_BASE_RADIUS,
                BASE_DESTRUCTION_BASE_HEALTH,
                1 // Team 1
        );

        // Team 2 base (bottom-right corner)
        double team2BaseX = GAME_WIDTH * 0.85; // 80% across the map
        double team2BaseY = GAME_HEIGHT * 0.7; // 80% down the map

        Vector2D team2BasePosition = new Vector2D(team2BaseX, team2BaseY);
        this.team2Base = new Base(
                Config.ID_COUNTER.incrementAndGet(),
                team2BasePosition,
                BASE_DESTRUCTION_BASE_RADIUS,
                BASE_DESTRUCTION_BASE_HEALTH,
                2 // Team 2
        );

        log.info("Generated Team 1 base at position ({}, {}) and Team 2 base at position ({}, {}) with {} health each",
                team1BaseX, team1BaseY, team2BaseX, team2BaseY, BASE_DESTRUCTION_BASE_HEALTH);
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        boolean invalidPosition;
        do {
            invalidPosition = false;
            double x, y;

            if (player.getTeam() == 1) {
                // Team 1 spawns on the left side, near their base
                double spawnableWidth = (GAME_WIDTH * 0.3) - Config.SPAWN_HORIZONTAL_PADDING;
                x = Config.SPAWN_HORIZONTAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableWidth;
            } else {
                // Team 2 spawns on the right side, near their base
                double minX = GAME_WIDTH * 0.7;
                double maxX = GAME_WIDTH - Config.SPAWN_HORIZONTAL_PADDING;
                x = minX + ThreadLocalRandom.current().nextDouble() * (maxX - minX);
            }

            double spawnableHeight = GAME_HEIGHT - (2 * Config.SPAWN_VERTICAL_PADDING);
            y = Config.SPAWN_VERTICAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableHeight;

            player.setX(x);
            player.setY(y);

            // Check if spawn point is inside an obstacle
            if (physicsEngine.isColliding(player, entities.getObstacles())) {
                invalidPosition = true;
                continue;
            }

            // Ensure players don't spawn too close to their own base
            Base teamBase = (player.getTeam() == 1) ? team1Base : team2Base;
            if (teamBase != null) {
                double distanceToBase = player.position().distance(teamBase.position());
                if (distanceToBase < BASE_DESTRUCTION_BASE_RADIUS + 60) { // 60 pixels minimum distance
                    invalidPosition = true;
                }
            }

            // Ensure players don't spawn too close to motor pools
            for (MotorPool pool : motorPools) {
                double distanceToPool = player.position().distance(pool.position());
                if (distanceToPool < MOTOR_POOL_RADIUS + 40) { // 40 pixels minimum distance
                    invalidPosition = true;
                    break;
                }
            }
        } while (invalidPosition);
    }

    @Override
    protected void sendVictoryMessage() {
        if (sentVictoryMessage) {
            return;
        }
        sentVictoryMessage = true;

        if (team1BaseDestroyed && team2BaseDestroyed) {
            sendGameEvent(GameEvent.info("Draw! Both bases were destroyed!"));
        } else if (team1BaseDestroyed) {
            sendGameEvent(GameEvent.team(2, "Team 2 wins!"));
        } else if (team2BaseDestroyed) {
            sendGameEvent(GameEvent.team(1, "Team 1 wins!"));
        } else {
            sendGameEvent(GameEvent.info("Draw! Both bases survived!"));
        }
    }
}
