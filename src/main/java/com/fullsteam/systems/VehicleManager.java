package com.fullsteam.systems;

import com.fullsteam.Config;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.vehicles.FixedCannon;
import com.fullsteam.model.vehicles.Jeep;
import com.fullsteam.model.vehicles.Mech;
import com.fullsteam.model.vehicles.Tank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static com.fullsteam.Config.VEHICLE_ACTION_DEBOUNCE_MS;

/**
 * Handles all vehicle-related operations including spawning, updates, player interactions,
 * and lifecycle management. Separated from AbstractGameStateManager to follow
 * Single Responsibility Principle.
 */
public class VehicleManager {

    private static final Logger log = LoggerFactory.getLogger(VehicleManager.class);

    private final GameEntities entities;
    private final PhysicsEngine physicsEngine;
    private final WeaponSystem weaponSystem;
    private final FieldEffectSystem fieldEffectSystem;
    private final Consumer<GameEvent> gameEventSender;

    public VehicleManager(GameEntities entities, PhysicsEngine physicsEngine,
                          WeaponSystem weaponSystem, FieldEffectSystem fieldEffectSystem, Consumer<GameEvent> gameEventSender) {
        this.entities = entities;
        this.physicsEngine = physicsEngine;
        this.weaponSystem = weaponSystem;
        this.fieldEffectSystem = fieldEffectSystem;
        this.gameEventSender = gameEventSender;
    }

    /**
     * Updates all vehicles, handles destruction, and weapon reloading
     */
    public void updateVehicles(long delta) {
        // Check mounted weapon reload completion for all vehicles
        for (Vehicle vehicle : entities.getVehicles()) {
            for (Vehicle.MountedWeapon mountedWeapon : vehicle.getMountedWeapons()) {
                if (mountedWeapon != null && mountedWeapon.isReloading() &&
                    System.currentTimeMillis() >= mountedWeapon.getReloadCompleteTime()) {
                    mountedWeapon.finishReload();
                }
            }
        }

        // Remove destroyed vehicles
        entities.getVehicles().removeIf(vehicle -> {
            if (vehicle.isDestroyed()) {
                // Create explosion when vehicle is destroyed
                fieldEffectSystem.createExplosion(
                        vehicle.position().x(),
                        vehicle.position().y(),
                        0, // No owner for vehicle explosions
                        0, // No team for vehicle explosions
                        vehicle.getBoundingRadius(), // explosion size based on vehicle size
                        0, // No damage from explosion effect itself
                        500); // Duration
                return true;
            }
            return false;
        });
    }

    /**
     * Handles player input for a player in a vehicle
     */
    public void handlePlayerVehicleInput(Long playerId, PlayerInput input, long delta) {
        Vehicle vehicle = getPlayerVehicle(playerId);
        if (vehicle == null) {
            return;
        }

        Player player = entities.getPlayer(playerId);
        if (player == null) {
            return;
        }

        // Player is in a vehicle, don't apply normal movement
        player.setVelocity(Vector2D.ZERO);
        player.setX(vehicle.position().x());
        player.setY(vehicle.position().y());

        // Handle seat cycling (Action1)
        if (input.isAction1()) {
            long currentTime = System.currentTimeMillis();
            Long lastActionTime = entities.getLastVehicleActionTime(playerId);
            if (lastActionTime == null || currentTime - lastActionTime >= VEHICLE_ACTION_DEBOUNCE_MS) {
                entities.setLastVehicleActionTime(playerId, currentTime);
                vehicle.cycleSeats(player);
            }
        }

        // Handle driver controls
        if (Objects.equals(vehicle.getDriverId(), player.id())) {
            updateVehiclePhysics(vehicle, input, delta);
        }

        // Handle weapon firing for all vehicle occupants
        handleVehicleWeaponFiring(vehicle, playerId, input);
    }

    /**
     * Updates vehicle physics and collision handling
     */
    private void updateVehiclePhysics(Vehicle vehicle, PlayerInput input, long delta) {
        if (vehicle.isDestroyed()) {
            return;
        }

        // Update vehicle physics
        Vector2D startingPosition = vehicle.position();
        double startingAngle = vehicle.getAngle();
        vehicle.handleDriverInput(input, delta);
        vehicle.update(delta);

        // Check collision with obstacles
        // Simple collision response - stop the vehicle
        if (physicsEngine.isColliding(vehicle, entities.getObstacles()) || physicsEngine.isColliding(vehicle, entities.getVehicles())) {
            vehicle.setVelocityX(0);
            vehicle.setVelocityY(0);
            vehicle.setPosition(startingPosition); // reset to starting position
            // TODO: handle rotation back to original?
        }
    }

    /**
     * Handles weapon firing for vehicle occupants
     */
    public void handleVehicleWeaponFiring(Vehicle vehicle, Long playerId, PlayerInput input) {
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

    /**
     * Handles vehicle enter/exit logic
     */
    public void handleVehicleEnterExit(Player player) {
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

                gameEventSender.accept(GameEvent.info("Exited " + currentVehicle.getVehicleName(), player.id()));
            }
        } else {
            // Try to enter a nearby vehicle
            Vehicle nearbyVehicle = findNearbyVehicle(player);
            if (nearbyVehicle != null && !nearbyVehicle.isDestroyed()) {
                if (nearbyVehicle.enterVehicle(player)) {
                    gameEventSender.accept(GameEvent.info("Entered " + nearbyVehicle.getVehicleName(), player.id()));
                } else {
                    gameEventSender.accept(GameEvent.red("Vehicle is full", player.id()));
                }
            } else {
                gameEventSender.accept(GameEvent.red("No vehicle nearby", player.id()));
            }
        }
    }

    /**
     * Gets the vehicle a player is currently in
     */
    public Vehicle getPlayerVehicle(Long playerId) {
        for (Vehicle vehicle : entities.getVehicles()) {
            if (vehicle.isPlayerInVehicle(playerId)) {
                return vehicle;
            }
        }
        return null;
    }

    /**
     * Finds a vehicle near the player within interaction radius
     */
    public Vehicle findNearbyVehicle(Player player) {
        double interactionRadius = Config.VEHICLE_INTERACTION_RADIUS;
        for (Vehicle vehicle : entities.getVehicles()) {
            if (!vehicle.isDestroyed()) {
                double distance = player.position().distance(vehicle.position());
                if (distance <= interactionRadius) {
                    return vehicle;
                }
            }
        }
        return null;
    }

    public void resetVehicles() {
        for (Vehicle vehicle : entities.getVehicles()) {
            vehicle.clearSeats();
        }
    }

    /**
     * Spawns a specific vehicle type at a random valid position
     */
    public Vehicle spawnVehicle(Vehicle.VehicleType type) {
        // Try to find a valid position
        Vehicle vehicle = createVehicle(type);
        double randomAngle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        vehicle.setAngle(randomAngle);
        vehicle.rotate(randomAngle);
        double bestX = Config.GAME_WIDTH / 2.0;
        double bestY = Config.GAME_HEIGHT / 2.0;
        boolean foundValidPosition = false;

        for (int attempts = 0; attempts < 20; attempts++) {
            double x = ThreadLocalRandom.current().nextDouble(50, Config.GAME_WIDTH - 50);
            double y = ThreadLocalRandom.current().nextDouble(50, Config.GAME_HEIGHT - 50);
            Vector2D testPosition = new Vector2D(x, y);

            if (physicsEngine.isValidVehiclePosition(vehicle, testPosition, 40)) {
                vehicle.setPosition(testPosition);
                entities.getVehicles().add(vehicle);
                foundValidPosition = true;
                log.info("Successfully spawned {} at ({}, {}) with rotation {}", type, x, y, Math.toDegrees(randomAngle));
                break;
            } else {
                bestX = x;
                bestY = y;
            }
        }

        if (!foundValidPosition) {
            log.warn("Could not find valid position for {} after 20 attempts, spawning at ({}, {}) with rotation {} anyway",
                    type, bestX, bestY, Math.toDegrees(randomAngle));
        }
        return vehicle;
    }

    /**
     * Creates a new vehicle of the specified type
     */
    public Vehicle createVehicle(Vehicle.VehicleType type) {
        return switch (type) {
            case TANK -> new Tank();
            case MECH -> new Mech();
            case JEEP -> new Jeep();
            case FIXED_CANNON -> new FixedCannon();
        };
    }
}
