package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GravityWell;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.Mine;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PoisonCloud;
import com.fullsteam.model.SmokeCloud;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.fullsteam.Config.PLAYER_RADIUS;

/**
 * Handles all field effects including explosions, poison clouds, mines, smoke fields,
 * and slow fields. Separated from AbstractGameStateManager to follow
 * Single Responsibility Principle.
 */
public class FieldEffectSystem {

    private final GameEntities entities;
    private final BiConsumer<Player, Player> killPlayerHandler;

    public FieldEffectSystem(GameEntities entities, BiConsumer<Player, Player> killPlayerHandler) {
        this.entities = entities;
        this.killPlayerHandler = killPlayerHandler;
    }

    /**
     * Updates all field effects, applies their effects, and removes expired ones
     */
    public void updateFieldEffects(long delta) {
        for (FieldEffect fieldEffect : List.copyOf(entities.getFieldEffects())) {
            switch (fieldEffect) {
                case Explosion explosion -> updateExplosion(explosion);
                case PoisonCloud poisonCloud -> updatePoisonClouds(poisonCloud);
                case SmokeCloud smokeCloud -> updateSmokeField(smokeCloud);
                case Mine mine -> updateMineField(mine);
                case GravityWell gravityWell -> updateGravityWell(gravityWell);
                case null, default ->
                        throw new UnsupportedOperationException("unsupported field effect type: " + fieldEffect.getClass().getSimpleName());
            }
        }
        // Next, remove any effects that have exceeded their duration.
        entities.getFieldEffects().removeIf(FieldEffect::isExpired);
    }

    public void updateGravityWellAffect(Player player) {
        for (FieldEffect fieldEffect : List.copyOf(entities.getFieldEffects())) {
            if (fieldEffect instanceof GravityWell gravityWell) {
                updateGravityWell(gravityWell, player);
            }
        }
    }

    private void updateGravityWell(GravityWell gravityWell, Player player) {
        Vector2D gravityCenter = gravityWell.position();
        if (!player.isDead()) {
            Vector2D playerPos = player.position();
            double distanceFromCenter = playerPos.distance(gravityCenter);

            // Only apply gravity if player is within the gravity well radius
            if (distanceFromCenter < gravityWell.getRadius()) {
                // Calculate gravity force based on distance
                double gravityForce = gravityWell.calculateGravityForce(distanceFromCenter);

                if (gravityForce > 0) {
                    // Calculate direction toward the gravity well center
                    Vector2D gravityDirection = gravityCenter.subtract(playerPos);
                    if (gravityDirection.magnitudeSq() > 0.001) { // Avoid division by zero
                        gravityDirection = gravityDirection.normalize();

                        // Apply gravity as a velocity modification
                        Vector2D gravityVector = gravityDirection.multiply(gravityForce * 0.01); // Scale for game balance
                        Vector2D newVelocity = player.getVelocity().add(gravityVector);

                        // Limit maximum velocity to prevent too extreme effects
                        double maxVelocity = player.getSpeed() * 1.5; // Allow 50% over normal speed
                        newVelocity = newVelocity.limit(maxVelocity);

                        player.setVelocity(newVelocity);
                    }
                }
            }
        }
    }

    /**
     * Handles the lifecycle of explosions, applying damage and removing them when expired.
     */
    private void updateExplosion(Explosion explosion) {
        // apply damage for any new explosions that haven't dealt it yet.
        if (!explosion.hasDamageBeenApplied()) {
            Vector2D explosionCenter = new Vector2D(explosion.getX(), explosion.getY());
            Player shooter = entities.getPlayer(explosion.getShooterId());

            Set<Targetable> nearbyTargets = entities.getTargetGrid().getNearby(explosion.position(), explosion.getRadius());
            for (Targetable target : nearbyTargets) {
                switch (target) {
                    case Player player -> {
                        if (player.isDead()) {
                            continue;
                        }

                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && player.getTeam() == shooter.getTeam() && !Objects.equals(player.getId(), shooter.getId())) {
                            continue;
                        }

                        if (player.position().distanceSquared(explosionCenter) < explosion.getRadiusSquared()) {
                            if (player.takeDamage(explosion.getDamage())) {
                                killPlayerHandler.accept(player, shooter);
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
                    case Obstacle obstacle -> {
                        if (obstacle instanceof HasLife hasLife && CollisionUtils.checkCirclePolygonCollision(
                                explosionCenter,
                                explosion.getRadius(),
                                obstacle.getVertices())) {
                            // If the explosion hits a destructible obstacle, apply damage to it.
                            hasLife.takeDamage(explosion.getDamage());
                        }
                    }
                    case null, default ->
                            throw new UnsupportedOperationException("unsupported explosion target type: " + target);
                }
            }
            explosion.markDamageApplied(); // Mark it so damage isn't applied again.
        }
    }

    /**
     * Handles the lifecycle of poison clouds, applying damage over time and removing them when expired.
     */
    private void updatePoisonClouds(PoisonCloud cloud) {
        long currentTime = System.currentTimeMillis();
        // Damage players inside the cloud, ticking every 500ms
        if (currentTime > cloud.getLastDamageTickTime() + 500) {
            Vector2D cloudCenter = cloud.position();
            double radiusSq = cloud.getRadiusSquared();
            Player shooter = entities.getPlayer(cloud.getShooterId());

            Set<Targetable> nearbyTargets = entities.getTargetGrid().getNearby(cloud.position(), cloud.getRadius());
            for (Targetable target : nearbyTargets) {
                switch (target) {
                    case Player player -> {
                        if (player.isDead()) {
                            continue;
                        }
                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && player.getTeam() == shooter.getTeam() && !Objects.equals(player.getId(), shooter.getId())) {
                            continue;
                        }

                        if (player.position().distanceSquared(cloudCenter) < radiusSq) {
                            if (player.takeDamage(cloud.getDamagePerTick())) {
                                killPlayerHandler.accept(player, shooter);
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
                        // poison doesn't apply to other target types
                    }
                }
            }
            cloud.setLastDamageTickTime(currentTime);
        }
    }

    /**
     * Handles smoke fields that obscure player vision
     */
    private void updateSmokeField(SmokeCloud smokeCloud) {
        Set<Targetable> nearbyTargets = entities.getTargetGrid().getNearby(smokeCloud.position(), smokeCloud.getRadius());
        for (Targetable target : nearbyTargets) {
            if (target instanceof Player player && !player.isDead()) {
                if (player.position().distanceSquared(smokeCloud.position()) < smokeCloud.getRadiusSquared()) {
                    player.setVisionObscured(true);
                }
            }
        }
    }

    /**
     * Handles mines that trigger when players get close
     */
    private void updateMineField(Mine mine) {
        Set<Targetable> nearbyTargets = entities.getTargetGrid().getNearby(mine.position(), mine.getRadius());
        for (Targetable target : nearbyTargets) {
            if (target instanceof Player player && !player.isDead() && mine.getTeam() != player.getTeam()) {
                // If the player is within the mine's radius, trigger the explosion
                if (player.position().distanceSquared(mine.position()) < Math.pow(mine.getRadius() + PLAYER_RADIUS, 2)) {
                    // Trigger the explosion effect
                    entities.getFieldEffects().add(Mine.mineExplosion(mine));
                    mine.markTriggered();
                }
            } else if (target instanceof Vehicle vehicle && !vehicle.isDestroyed() && vehicle.getTeam() != mine.getTeam()) {
                if (CollisionUtils.checkCirclePolygonCollision(mine.position(), mine.getRadius(), vehicle.getVertices())) {
                    entities.getFieldEffects().add(Mine.mineExplosion(mine));
                    mine.markTriggered();
                }
            }
        }
    }

    /**
     * Adds a field effect to the game
     */
    public void addFieldEffect(FieldEffect fieldEffect) {
        entities.getFieldEffects().add(fieldEffect);
    }

    /**
     * Creates an explosion at the specified location
     */
    public void createExplosion(double x, double y, long shooterId, int team, double radius, double damage,
                                long duration) {
        Explosion explosion = new Explosion(x, y, shooterId, team, radius, damage, duration);
        addFieldEffect(explosion);
    }

    /**
     * Handles gravity wells that pull players toward their center
     */
    private void updateGravityWell(GravityWell gravityWell) {
        Set<Targetable> nearbyTargets = entities.getTargetGrid().getNearby(gravityWell.position(), gravityWell.getRadius());
        Vector2D gravityCenter = gravityWell.position();

        for (Targetable target : nearbyTargets) {
            if (target instanceof Player player && !player.isDead()) {
                Vector2D playerPos = player.position();
                double distanceFromCenter = playerPos.distance(gravityCenter);

                // Only apply gravity if player is within the gravity well radius
                if (distanceFromCenter < gravityWell.getRadius()) {
                    // Calculate gravity force based on distance
                    double gravityForce = gravityWell.calculateGravityForce(distanceFromCenter);

                    if (gravityForce > 0) {
                        // Calculate direction toward the gravity well center
                        Vector2D gravityDirection = gravityCenter.subtract(playerPos);
                        if (gravityDirection.magnitudeSq() > 0.001) { // Avoid division by zero
                            gravityDirection = gravityDirection.normalize();

                            // Apply gravity as a velocity modification
                            Vector2D gravityVector = gravityDirection.multiply(gravityForce * 0.01); // Scale for game balance
                            Vector2D newVelocity = player.getVelocity().add(gravityVector);

                            // Limit maximum velocity to prevent too extreme effects
                            double maxVelocity = player.getSpeed() * 1.5; // Allow 50% over normal speed
                            newVelocity = newVelocity.limit(maxVelocity);

                            player.setVelocity(newVelocity);
                        }
                    }
                }
            } else if (target instanceof Vehicle vehicle && !vehicle.isDestroyed()) {
                // Apply gravity to vehicles as well, but with reduced effect
                Vector2D vehiclePos = vehicle.position();
                double distanceFromCenter = vehiclePos.distance(gravityCenter);

                if (distanceFromCenter < gravityWell.getRadius()) {
                    double gravityForce = gravityWell.calculateGravityForce(distanceFromCenter) * 0.3; // Reduced effect for vehicles

                    if (gravityForce > 0) {
                        Vector2D gravityDirection = gravityCenter.subtract(vehiclePos);
                        if (gravityDirection.magnitudeSq() > 0.001) {
                            gravityDirection = gravityDirection.normalize();

                            // Apply gravity to vehicle velocity components
                            Vector2D gravityVector = gravityDirection.multiply(gravityForce * 0.005); // Even smaller scale for vehicles
                            vehicle.setVelocityX(vehicle.getVelocityX() + gravityVector.x());
                            vehicle.setVelocityY(vehicle.getVelocityY() + gravityVector.y());
                        }
                    }
                }
            }
        }
    }
}
