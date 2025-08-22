package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Turret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

import static com.fullsteam.Config.MAX_TURRETS_PER_PLAYER;
import static com.fullsteam.Config.PLAYER_SIZE;

/**
 * Handles all turret-related functionality including AI, firing logic, 
 * lifecycle management, and placement limits. Separated from AbstractGameStateManager 
 * to follow Single Responsibility Principle.
 */
public class TurretSystem {

    private static final Logger log = LoggerFactory.getLogger(TurretSystem.class);

    private final GameEntities entities;
    private final WeaponSystem weaponSystem;
    private final FieldEffectSystem fieldEffectSystem;
    private final Consumer<GameEvent> gameEventSender;

    public TurretSystem(GameEntities entities, WeaponSystem weaponSystem, 
                       FieldEffectSystem fieldEffectSystem, Consumer<GameEvent> gameEventSender) {
        this.entities = entities;
        this.weaponSystem = weaponSystem;
        this.fieldEffectSystem = fieldEffectSystem;
        this.gameEventSender = gameEventSender;
    }

    /**
     * Updates all turrets, handles AI decisions, firing, and removal of destroyed turrets
     */
    public void updateTurrets(long delta) {
        // Create a game state snapshot for turret AI
        GameState gameState = new GameState(
                entities.getPlayers().values().stream()
                        .filter(p -> p.getInvisibilityEndTime() < System.currentTimeMillis())
                        .toList(),
                entities.getBullets(),
                entities.getLaserBlasts(),
                entities.getFieldEffects(),
                entities.getTurrets(),
                entities.getVehicles(),
                entities.getObstacles(),
                entities.getPowerUps(),
                System.currentTimeMillis(),
                null // GameInfo is not needed for turret AI
        );

        // Remove destroyed or invalid turrets
        entities.getTurrets().removeIf(turret -> {
            // Check if turret is inside an obstacle
            boolean inObstacle = entities.getObstacles().stream()
                    .anyMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(
                            turret.position(), turret.getRadius(), obstacle.vertices()));
            if (inObstacle) {
                log.debug("Removing turret {} - inside obstacle", turret.getId());
                return true;
            }

            // Check if turret is destroyed
            if (turret.getHp() <= 0) {
                log.debug("Turret {} destroyed, creating explosion", turret.getId());
                // Create explosion when turret is destroyed
                fieldEffectSystem.createExplosion(
                        turret.getX(),
                        turret.getY(),
                        turret.getOwnerId(),
                        turret.getTeam(),
                        PLAYER_SIZE, // explosion radius
                        0, // no damage from explosion effect itself
                        300); // duration
                return true;
            }
            return false;
        });

        // Update remaining turrets
        for (Turret turret : entities.getTurrets()) {
            turret.update(gameState, entities.getTargetGrid())
                    .ifPresent(action -> {
                        double aimAngle = Math.atan2(action.directionY(), action.directionX());
                        weaponSystem.fireTurretWeapon(turret, aimAngle);
                    });
        }
    }

    /**
     * Attempts to place a turret, enforcing player limits
     */
    public boolean placeTurret(Turret turret) {
        long ownerId = turret.getOwnerId();
        long existingTurrets = entities.getTurrets().stream()
                .filter(existing -> existing.getOwnerId() == ownerId)
                .count();

        if (existingTurrets < MAX_TURRETS_PER_PLAYER) {
            entities.getTurrets().add(turret);
            log.debug("Placed turret {} for player {}", turret.getId(), ownerId);
            return true;
        } else {
            // Send feedback message to the player who tried to place the turret
            gameEventSender.accept(GameEvent.red("Turret limit reached!", ownerId));
            log.debug("Turret placement denied for player {} - limit reached", ownerId);
            return false;
        }
    }

    /**
     * Gets all turrets (read-only access)
     */
    public java.util.List<Turret> getTurrets() {
        return java.util.Collections.unmodifiableList(entities.getTurrets());
    }

    /**
     * Gets turrets owned by a specific player
     */
    public java.util.List<Turret> getTurretsOwnedBy(long playerId) {
        return entities.getTurrets().stream()
                .filter(turret -> turret.getOwnerId() == playerId)
                .toList();
    }

    /**
     * Counts turrets owned by a specific player
     */
    public long countTurretsOwnedBy(long playerId) {
        return entities.getTurrets().stream()
                .filter(turret -> turret.getOwnerId() == playerId)
                .count();
    }

    /**
     * Removes all turrets owned by a specific player
     */
    public void removeAllTurretsOwnedBy(long playerId) {
        long removedCount = entities.getTurrets().stream()
                .filter(turret -> turret.getOwnerId() == playerId)
                .count();
        entities.getTurrets().removeIf(turret -> turret.getOwnerId() == playerId);
        if (removedCount > 0) {
            log.debug("Removed {} turrets owned by player {}", removedCount, playerId);
        }
    }

    /**
     * Destroys a specific turret (triggers explosion)
     */
    public void destroyTurret(Turret turret) {
        turret.takeDamage(turret.getHp()); // Deal enough damage to destroy it
        // The updateTurrets method will handle the explosion and removal
    }

    /**
     * Removes all turrets from the game
     */
    public void removeAllTurrets() {
        int removedCount = entities.getTurrets().size();
        entities.getTurrets().clear();
        if (removedCount > 0) {
            log.debug("Removed all {} turrets from the game", removedCount);
        }
    }

    /**
     * Checks if a turret can be placed at the specified location
     */
    public boolean canPlaceTurretAt(double x, double y, double radius) {
        // Check if location is inside any obstacle
        return entities.getObstacles().stream()
                .noneMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(
                        new com.fullsteam.model.Vector2D(x, y), radius, obstacle.vertices()));
    }

    /**
     * Gets the maximum number of turrets a player can place
     */
    public int getMaxTurretsPerPlayer() {
        return MAX_TURRETS_PER_PLAYER;
    }

    /**
     * Checks if a player has reached their turret limit
     */
    public boolean hasReachedTurretLimit(long playerId) {
        return countTurretsOwnedBy(playerId) >= MAX_TURRETS_PER_PLAYER;
    }

    /**
     * Gets turrets within a specific radius of a point
     */
    public java.util.List<Turret> getTurretsNear(double x, double y, double radius) {
        com.fullsteam.model.Vector2D center = new com.fullsteam.model.Vector2D(x, y);
        return entities.getTurrets().stream()
                .filter(turret -> turret.position().distanceSquared(center) <= radius * radius)
                .toList();
    }

    /**
     * Gets the total number of turrets in the game
     */
    public int getTotalTurretCount() {
        return entities.getTurrets().size();
    }
}
