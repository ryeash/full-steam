package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.Mine;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PoisonCloud;
import com.fullsteam.model.SlowField;
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
                case SlowField slowField -> updateSlowField(slowField);
                case SmokeCloud smokeCloud -> updateSmokeField(smokeCloud);
                case Mine mine -> updateMineField(mine);
                case null, default ->
                        throw new UnsupportedOperationException("unsupported field effect type: " + fieldEffect.getClass().getSimpleName());
            }
        }
        // Next, remove any effects that have exceeded their duration.
        entities.getFieldEffects().removeIf(FieldEffect::isExpired);
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
     * Handles slow fields that reduce player movement speed
     */
    private void updateSlowField(SlowField slowField) {
        Set<Targetable> nearbyTargets = entities.getTargetGrid().getNearby(slowField.position(), slowField.getRadius());
        for (Targetable target : nearbyTargets) {
            if (target instanceof Player player && !player.isDead() && player.getTeam() != slowField.getTeam()) {
                if (player.position().distanceSquared(slowField.position()) < slowField.getRadiusSquared()) {
                    player.setSpeed(player.getDefaultSpeed() * slowField.getSlowFactor());
                }
            }
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
     * Gets all current field effects (read-only access)
     */
    public List<FieldEffect> getFieldEffects() {
        return java.util.Collections.unmodifiableList(entities.getFieldEffects());
    }

    /**
     * Removes all field effects of a specific type
     */
    public void removeFieldEffectsOfType(Class<? extends FieldEffect> effectType) {
        entities.getFieldEffects().removeIf(effectType::isInstance);
    }

    /**
     * Counts field effects of a specific type
     */
    public long countFieldEffectsOfType(Class<? extends FieldEffect> effectType) {
        return entities.getFieldEffects().stream()
                .filter(effectType::isInstance)
                .count();
    }

    /**
     * Creates an explosion at the specified location
     */
    public void createExplosion(double x, double y, long shooterId, int team, double radius, double damage, long duration) {
        Explosion explosion = new Explosion(x, y, shooterId, team, radius, damage, duration);
        addFieldEffect(explosion);
    }

    /**
     * Creates a poison cloud at the specified location
     */
    public void createPoisonCloud(double x, double y, long shooterId, int team, double radius, double damagePerTick, long duration) {
        PoisonCloud poisonCloud = new PoisonCloud(x, y, shooterId, team, radius, damagePerTick, duration);
        addFieldEffect(poisonCloud);
    }

    /**
     * Creates a slow field at the specified location
     */
    public void createSlowField(double x, double y, long shooterId, int team, double radius, double slowFactor, long duration) {
        SlowField slowField = new SlowField(x, y, shooterId, team, radius, slowFactor, duration);
        addFieldEffect(slowField);
    }

    /**
     * Creates a smoke cloud at the specified location
     */
    public void createSmokeCloud(double x, double y, long shooterId, int team, double radius, long duration) {
        SmokeCloud smokeCloud = new SmokeCloud(x, y, shooterId, team, radius, duration);
        addFieldEffect(smokeCloud);
    }

    /**
     * Creates a mine at the specified location
     */
    public void createMine(double x, double y, long ownerId, int team, double radius, long expiration) {
        Mine mine = new Mine(com.fullsteam.Config.ID_COUNTER.incrementAndGet(), team, x, y, radius, expiration, ownerId);
        addFieldEffect(mine);
    }
}
