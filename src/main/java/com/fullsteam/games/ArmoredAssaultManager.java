package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.MotorPool;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.gamemodes.ArmoredAssaultInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.MOTOR_POOL_CONTROL_TIME_MS;
import static com.fullsteam.Config.MOTOR_POOL_RADIUS;
import static com.fullsteam.Config.VEHICLE_REPAIR_RATE;

@Prototype
public class ArmoredAssaultManager extends AbstractTeamBasedManager {

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
    public ArmoredAssaultManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        initializeMotorPools();
    }

    private void initializeMotorPools() {
        motorPools = new ArrayList<>();

        // Team 1 motor pool (left side)
        Vector2D team1Position = new Vector2D(MOTOR_POOL_RADIUS + 50, GAME_HEIGHT / 2.0);
        MotorPool team1Pool = new MotorPool(
                team1Position,
                MOTOR_POOL_RADIUS,
                MOTOR_POOL_RADIUS * MOTOR_POOL_RADIUS,
                0, // initially neutral
                false, // not contested
                0, // no control start time
                MOTOR_POOL_CONTROL_TIME_MS,
                1
        );

        // Team 2 motor pool (right side)
        Vector2D team2Position = new Vector2D(GAME_WIDTH - MOTOR_POOL_RADIUS - 50, GAME_HEIGHT / 2.0);
        MotorPool team2Pool = new MotorPool(
                team2Position,
                MOTOR_POOL_RADIUS,
                MOTOR_POOL_RADIUS * MOTOR_POOL_RADIUS,
                0, // initially neutral
                false, // not contested
                0, // no control start time
                MOTOR_POOL_CONTROL_TIME_MS,
                2
        );

        motorPools.add(team1Pool);
        motorPools.add(team2Pool);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();

        // Clear existing vehicles
        vehicleManager.resetVehicles();
        entities.getVehicles().clear();

        // Reset motor pool control states
        motorPools.replaceAll(motorPool -> motorPool.withState(0, false, 0));

        team1VehicleIndex = 0;
        team2VehicleIndex = 0;

        // give each team one FixedCannon
        for (int i = 0; i < motorPools.size(); i++) {
            MotorPool motorPool = motorPools.get(i);
            Vector2D position = motorPool.position();
            Vehicle fixedCannon = vehicleManager.spawnVehicle(Vehicle.VehicleType.FIXED_CANNON);
            fixedCannon.setPosition(new Vector2D(position.x(), i == 0 ? 150 : GAME_HEIGHT - 150));
        }
    }

    @Override
    protected void generateObstacles() {
        entities.getObstacles().clear();

        // Create a large rectangular obstacle in the center of the map to block
        // line of sight between the two motor pools.
        double obstacleWidth = 80.0; // A thick central wall
        double obstacleHeight = MOTOR_POOL_RADIUS; // Covers 60% of the map height
        double x = (Config.GAME_WIDTH / 2.0) - (obstacleWidth / 2.0);
        double y = (Config.GAME_HEIGHT / 2.0) - (obstacleHeight / 2.0);

        entities.getObstacles().add(Obstacle.createRectangle(x, y + MOTOR_POOL_RADIUS, obstacleWidth, obstacleHeight));
        entities.getObstacles().add(Obstacle.createRectangle(x, y - MOTOR_POOL_RADIUS, obstacleWidth, obstacleHeight));
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (shooter != null) {
            if (shooter.getTeam() == 1) {
                team1Score++;
            } else {
                team2Score++;
            }
        }
    }

    @Override
    protected void updateGame(long delta) {
        updateMotorPools();
        handleVehicleRepair();
        super.updateGame(delta);
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
        // Try to spawn near the motor pool
        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            double distance = MOTOR_POOL_RADIUS + 60 + ThreadLocalRandom.current().nextDouble() * 40;

            double x = poolPosition.x() + Math.cos(angle) * distance;
            double y = poolPosition.y() + Math.sin(angle) * distance;

            // Ensure within bounds
            x = Math.max(50, Math.min(GAME_WIDTH - 50, x));
            y = Math.max(50, Math.min(GAME_HEIGHT - 50, y));

            Vector2D candidate = new Vector2D(x, y);

            // Check if position is clear (simplified check)
            boolean clear = true;
            for (Vehicle existingVehicle : entities.getVehicles()) {
                if (candidate.distanceSquared(existingVehicle.position()) < 3600) { // 60px separation
                    clear = false;
                    break;
                }
            }

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
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new ArmoredAssaultInfo(
                team1Score,
                team2Score,
                timeLeft,
                motorPools);
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() >= roundEndTime) {
            sendVictoryMessage();
            log.info("Round timer has expired. Starting a new round.");
            return true;
        } else {
            return false;
        }
    }
}