package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Turret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

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

    public TurretSystem(GameEntities entities,
                        WeaponSystem weaponSystem,
                        FieldEffectSystem fieldEffectSystem) {
        this.entities = entities;
        this.weaponSystem = weaponSystem;
        this.fieldEffectSystem = fieldEffectSystem;
    }

    /**
     * Updates all turrets, handles AI decisions, firing, and removal of destroyed turrets
     */
    public void updateTurrets(long delta) {
        // Create a game state snapshot for turret AI
        GameState gameState = new GameState(
                entities.getPlayers().stream()
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

    public void placeTurret(Turret turret) {
        List<Turret> owned = new LinkedList<>();
        for (Turret t : entities.getTurrets()) {
            if (t.getOwnerId() == turret.getOwnerId()) {
                owned.add(t);
            }
        }
        while (owned.size() >= MAX_TURRETS_PER_PLAYER) {
            entities.getTurrets().remove(owned.removeFirst());
        }
        entities.getTurrets().add(turret);
    }

    /**
     * Removes all turrets owned by a specific player
     */
    public void removeAllTurretsOwnedBy(long playerId) {
        entities.getTurrets().removeIf(turret -> turret.getOwnerId() == playerId);
    }
}
