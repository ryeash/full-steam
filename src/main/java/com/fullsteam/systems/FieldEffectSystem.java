package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.model.Base;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.BulletScatter;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GameState;
import com.fullsteam.model.GravityWell;
import com.fullsteam.model.GridPoint;
import com.fullsteam.model.HasId;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.Mine;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PoisonCloud;
import com.fullsteam.model.Portal;
import com.fullsteam.model.SmokeCloud;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static com.fullsteam.Config.DEFENSE_GRID_LASER_MAX_PER_USER;
import static com.fullsteam.Config.MAX_PORTALS_PER_PLAYER;
import static com.fullsteam.Config.MAX_TURRETS_PER_PLAYER;
import static com.fullsteam.Config.PLAYER_RADIUS;
import static com.fullsteam.Config.PLAYER_SIZE;

/**
 * Handles all field effects including explosions, poison clouds, mines, smoke fields,
 * and slow fields. Separated from AbstractGameStateManager to follow
 * Single Responsibility Principle.
 */
public class FieldEffectSystem {

    private final WeaponSystem weaponSystem;
    private final GameEntities entities;
    private final BiConsumer<Player, Player> killPlayerHandler;

    public FieldEffectSystem(WeaponSystem weaponSystem, GameEntities entities, BiConsumer<Player, Player> killPlayerHandler) {
        this.weaponSystem = weaponSystem;
        this.entities = entities;
        this.killPlayerHandler = killPlayerHandler;
    }

    /**
     * Updates all field effects, applies their effects, and removes expired ones
     */
    public void updateFieldEffects(long delta) {
        for (FieldEffect fieldEffect : List.copyOf(entities.getFieldEffects().values())) {
            switch (fieldEffect) {
                case Explosion explosion -> updateExplosion(explosion);
                case PoisonCloud poisonCloud -> updatePoisonClouds(poisonCloud, delta);
                case SmokeCloud smokeCloud -> updateSmokeField(smokeCloud);
                case Mine mine -> updateMineField(mine);
                case GravityWell gravityWell -> updateGravityWell(gravityWell);
                case Turret turret -> updateTurrets(turret, delta);
                case GridPoint gridPoint -> updateGridPoint(gridPoint);
                case Portal portal -> updatePortal(portal, delta);
                case BulletScatter bulletScatter -> updateBulletScatter(bulletScatter);
                case null, default ->
                        throw new UnsupportedOperationException("unsupported field effect type: " + fieldEffect.getClass().getSimpleName());
            }
        }
        // Next, remove any effects that have exceeded their duration.
        entities.getFieldEffects().values().removeIf(FieldEffect::isExpired);
    }

    public void updateGravityWellAffect(Player player) {
        for (FieldEffect fieldEffect : List.copyOf(entities.getFieldEffects().values())) {
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
            explosion.markDamageApplied(); // Mark it so damage isn't applied again.
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
                    case GridPoint gridPoint -> {
                        // Prevent friendly fire, but allow self-damage
                        if (shooter != null && gridPoint.getTeam() == shooter.getTeam() && !Objects.equals(gridPoint.getId(), shooter.getId())) {
                            continue;
                        }
                        if (gridPoint.position().distanceSquared(explosionCenter) < explosion.getRadiusSquared()) {
                            gridPoint.takeDamage(explosion.getDamage());
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
                    case Base base -> {
                        // Check for collision with enemy base only (prevent friendly fire)
                        if (base.getTeam() != explosion.getTeam()
                            && CollisionUtils.checkCirclePolygonCollision(explosionCenter, explosion.getRadius(), base.getVertices())) {
                            base.takeDamage(explosion.getDamage());
                        }
                    }
                    case Obstacle obstacle -> {
                        if (obstacle instanceof HasLife hasLife
                            && CollisionUtils.checkCirclePolygonCollision(explosionCenter, explosion.getRadius(), obstacle.getVertices())) {
                            // If the explosion hits a destructible obstacle, apply damage to it.
                            hasLife.takeDamage(explosion.getDamage());
                        }
                    }
                    case null, default ->
                            throw new UnsupportedOperationException("unsupported explosion target type: " + target);
                }
            }
        }
    }

    /**
     * Handles the lifecycle of poison clouds, applying damage over time and removing them when expired.
     */
    private void updatePoisonClouds(PoisonCloud cloud, long delta) {
        Vector2D cloudCenter = cloud.position();
        double radiusSq = cloud.getRadiusSquared();
        Player shooter = entities.getPlayer(cloud.getShooterId());
        double damageToApply = cloud.getDamage(delta);

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
                        if (player.takeDamage(damageToApply)) {
                            killPlayerHandler.accept(player, shooter);
                        }
                    }
                }
                case Turret turret -> {
                    if (shooter != null && turret.getTeam() == shooter.getTeam() && !Objects.equals(turret.getId(), shooter.getId())) {
                        continue;
                    }
                    if (turret.position().distanceSquared(cloudCenter) < radiusSq) {
                        turret.takeDamage(damageToApply);
                    }
                }
                case GridPoint gridPoint -> {
                    if (shooter != null && gridPoint.getTeam() == shooter.getTeam() && !Objects.equals(gridPoint.getId(), shooter.getId())) {
                        continue;
                    }
                    if (gridPoint.position().distanceSquared(cloudCenter) < radiusSq) {
                        gridPoint.takeDamage(damageToApply);
                    }
                }
                case null, default -> {
                    // poison doesn't apply to other target types
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
                    addFieldEffect(Mine.mineExplosion(mine));
                    mine.markTriggered();
                }
            } else if (target instanceof Vehicle vehicle && !vehicle.isDestroyed() && vehicle.getTeam() != mine.getTeam()) {
                if (CollisionUtils.checkCirclePolygonCollision(mine.position(), mine.getRadius(), vehicle.getVertices())) {
                    addFieldEffect(Mine.mineExplosion(mine));
                    mine.markTriggered();
                }
            }
        }
    }

    /**
     * Adds a field effect to the game
     */
    public void addFieldEffect(FieldEffect fieldEffect) {
        entities.getFieldEffects().put(fieldEffect.id(), fieldEffect);
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

    /**
     * Handles defense grid points that generate lasers between connected points
     */
    private void updateGridPoint(GridPoint gridPoint) {
        long currentTime = System.currentTimeMillis();
        if (gridPoint.getHp() <= 0) {
            createExplosion(
                    gridPoint.getX(),
                    gridPoint.getY(),
                    gridPoint.getOwnerId(),
                    gridPoint.getTeam(),
                    PLAYER_SIZE, // explosion radius
                    0, // no damage from explosion effect itself
                    300); // duration
            entities.getFieldEffects().remove(gridPoint.id());
        }

        boolean inObstacle = entities.getObstacles().stream()
                .anyMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(
                        gridPoint.position(), gridPoint.getRadius(), obstacle.vertices()));
        if (inObstacle) {
            entities.getFieldEffects().remove(gridPoint.id());
            return;
        }

        // Check if enough time has passed since last laser generation
        if (!gridPoint.readyToFire()) {
            return;
        }

        // Find other grid points from the same team
        List<GridPoint> sameOwnerGridPoints = entities.getFieldEffects()
                .values()
                .stream()
                .filter(fe -> fe instanceof GridPoint)
                .map(fe -> (GridPoint) fe)
                .filter(gp -> gp.getTeam() == gridPoint.getTeam()
                              && gp.id() != gridPoint.id()
                              && gridPoint.readyToFire())
                .sorted(Comparator.comparingDouble(a -> gridPoint.position().distanceSquared(a.position())))
                .limit(2)
                .toList();

        // Generate lasers to nearby grid points within range
        double maxLaserRange = Config.DEFENSE_GRID_LASER_RANGE;
        double laserDamage = Config.DEFENSE_GRID_LASER_DAMAGE;
        long laserDuration = Config.DEFENSE_GRID_LASER_DURATION;
        double laserDamageOverTimeMod = (double) 1000 / Config.DEFENSE_GRID_LASER_DURATION;

        for (GridPoint targetPoint : sameOwnerGridPoints) {
            double distance = gridPoint.position().distance(targetPoint.position());
            // only grid points within line-of-sight will work
            if (CollisionUtils.checkAnyLinePolygonCollision(gridPoint.position(), targetPoint.position(), entities.getObstacles())) {
                continue;
            }

            if (distance <= maxLaserRange) {
                // Create laser blast between the two grid points
                LaserBlast laser = new LaserBlast(
                        gridPoint.position(),
                        targetPoint.position(),
                        gridPoint.getOwnerId(),
                        gridPoint.getTeam(),
                        laserDamage * laserDamageOverTimeMod,
                        currentTime + laserDuration
                );
                weaponSystem.calculateTerminus(laser);
                entities.getLaserBlasts().add(laser);

                // Update last laser time for both points to prevent spam
                gridPoint.setLastLaserTime(currentTime);
                targetPoint.setLastLaserTime(currentTime);
            }
        }
    }

    public void placeGridPoint(GridPoint gridPoint) {
        placeLimitedEffect(gridPoint, DEFENSE_GRID_LASER_MAX_PER_USER, fe -> fe instanceof GridPoint t && t.getOwnerId() == gridPoint.getOwnerId());
    }

    /**
     * Updates all turrets, handles AI decisions, firing, and removal of destroyed turrets
     */
    public void updateTurrets(Turret turret, long delta) {
        // Create a game state snapshot for turret AI
        GameState gameState = new GameState(
                entities.getPlayers().stream()
                        .filter(p -> p.getInvisibilityEndTime() < System.currentTimeMillis())
                        .toList(),
                entities.getBullets(),
                entities.getLaserBlasts(),
                entities.getFieldEffects().values(),
                entities.getVehicles(),
                entities.getObstacles(),
                entities.getPowerUps(),
                System.currentTimeMillis(),
                null // GameInfo is not needed for turret AI
        );

        // Remove destroyed or invalid turrets
        // Check if turret is inside an obstacle
        boolean inObstacle = entities.getObstacles().stream()
                .anyMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(
                        turret.position(), turret.getRadius(), obstacle.vertices()));
        if (inObstacle) {
            entities.getFieldEffects().remove(turret.id());
            return;
        }

        // Check if turret is destroyed
        if (turret.getHp() <= 0) {
            // Create explosion when turret is destroyed
            createExplosion(
                    turret.getX(),
                    turret.getY(),
                    turret.getOwnerId(),
                    turret.getTeam(),
                    PLAYER_SIZE, // explosion radius
                    0, // no damage from explosion effect itself
                    300); // duration
            entities.getFieldEffects().remove(turret.id());
            return;
        }
        turret.update(gameState, entities.getTargetGrid())
                .ifPresent(action -> {
                    double aimAngle = Math.atan2(action.directionY(), action.directionX());
                    weaponSystem.fireTurretWeapon(turret, aimAngle);
                });
    }

    public void placeTurret(Turret turret) {
        placeLimitedEffect(turret, MAX_TURRETS_PER_PLAYER, fe -> fe instanceof Turret t && t.getOwnerId() == turret.getOwnerId());
    }

    /**
     * Handles portal lifecycle and bullet teleportation
     */
    private void updatePortal(Portal portal, long delta) {
        // Remove if inside an obstacle
        boolean inObstacle = entities.getObstacles().stream()
                .anyMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(
                        portal.position(), portal.getRadius(), obstacle.vertices()));
        if (inObstacle) {
            entities.getFieldEffects().remove(portal.id());
            return;
        }
        if (portal.getLinkedTo() < 0 || entities.getFieldEffects().get(portal.getLinkedTo()) == null) {
            entities.getFieldEffects()
                    .values()
                    .stream()
                    .filter(fe -> fe instanceof Portal p
                                  && p.id() != portal.id()
                                  && p.getOwnerId() == portal.getOwnerId())
                    .findFirst()
                    .map(HasId::id)
                    .ifPresent(portal::setLinkedTo);
        }
        handleBulletTeleportation(portal, delta);
        handlePlayerTeleportation(portal, delta);
    }

    /**
     * Handles teleporting bullets through portals
     */
    private void handleBulletTeleportation(Portal portal, long delta) {
        Portal linkedPortal = (Portal) entities.getFieldEffects().get(portal.getLinkedTo());
        if (linkedPortal == null) {
            return;
        }

        // Check all bullets for teleportation (check trajectory intersection to catch fast bullets)
        List<Bullet> bulletsToTeleport = entities.getBullets()
                .stream()
                // Use line-circle intersection to detect if bullet path crosses portal
                .filter(bullet -> CollisionUtils.checkLineCircleCollision(bullet.previousPosition(), bullet.position(), portal.position(), portal.getRadius()))
                // Check if bullet is moving toward the portal center
                .filter(bullet -> {
                    Vector2D toPortalCenter = portal.position().subtract(bullet.position()).normalize();
                    double dotProduct = bullet.direction().normalize().dot(toPortalCenter);
                    return dotProduct > 0;
                })
                .toList();

        for (Bullet bullet : bulletsToTeleport) {
            // Calculate exit position maintaining relative offset
            Vector2D exitPosition = portal.calculateExitPosition(bullet.position(), linkedPortal);

            // Create a new bullet at the exit position with same properties
            Bullet teleportedBullet = new Bullet(
                    exitPosition.x(),
                    exitPosition.y(),
                    bullet.direction().x() * bullet.getSpeed(),
                    bullet.direction().y() * bullet.getSpeed(),
                    bullet.getShooterId(),
                    bullet.getTeam(),
                    bullet.getDamage(),
                    bullet.getSpeed(),
                    bullet.getMaxRange() - bullet.getDistanceTraveled(),
                    bullet.getBulletSpeedDecay(),
                    bullet.getOnDestructionAction().orElse(null)
            );

            // Remove the original bullet and add the teleported one
            entities.getBullets().remove(bullet);
            entities.getBullets().add(teleportedBullet);
        }
    }

    /**
     * Handles teleporting players through portals
     */
    private void handlePlayerTeleportation(Portal portal, long delta) {
        Portal linkedPortal = (Portal) entities.getFieldEffects().get(portal.getLinkedTo());
        if (linkedPortal == null) {
            return;
        }

        // Check all players for teleportation
        List<Player> playersToTeleport = entities.getPlayers()
                .stream()
                .filter(player -> !player.isDead())
                .filter(player -> {
                    // Check if player is within portal radius
                    double distanceToPortal = player.position().distance(portal.position());
                    return distanceToPortal <= portal.getRadius();
                })
                .filter(player -> {
                    // Check if player is moving toward the portal center (to prevent infinite loops)
                    Vector2D playerVelocity = player.getVelocity();
                    if (playerVelocity.magnitude() < 0.1) {
                        return true; // Allow teleportation for stationary players
                    }

                    Vector2D toPortalCenter = portal.position().subtract(player.position()).normalize();
                    double dotProduct = playerVelocity.normalize().dot(toPortalCenter);
                    return dotProduct > 0.1; // Small threshold to avoid jitter
                })
                .toList();

        for (Player player : playersToTeleport) {
            // Calculate exit position maintaining relative offset
            Vector2D exitPosition = portal.calculateExitPosition(player.position(), linkedPortal);

            // Make sure exit position doesn't put player inside obstacles
            boolean exitInObstacle = entities.getObstacles().stream()
                    .anyMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(
                            exitPosition, PLAYER_RADIUS, obstacle.vertices()));

            if (exitInObstacle) {
                continue; // Skip teleportation if exit would be inside obstacle
            }

            // Teleport the player
            player.setX(exitPosition.x());
            player.setY(exitPosition.y());

            // Preserve player velocity (maintain momentum through portal)
            // Optionally, we could mirror the velocity direction as well
        }
    }

    /**
     * Places a portal, managing the maximum limit per player
     */
    public void placePortal(Portal portal) {
        placeLimitedEffect(portal, MAX_PORTALS_PER_PLAYER, fe -> fe instanceof Portal p && p.getOwnerId() == portal.getOwnerId());
    }

    public void removePlayerPortal(Player player) {
        entities.getFieldEffects().values().removeIf(fe -> fe instanceof Portal p && p.getOwnerId() == player.id());
    }

    // Constants for properly handling the BulletScatter effect
    private static final double SPAWN_CHECK_RADIUS = 2.0; // small collision radius for spawn test
    private static final double INITIAL_OFFSET = 6.0;     // start a bit away from the effect center
    private static final double STEP = 3.0;               // step outward if inside obstacle
    private static final double MAX_DISTANCE = 24.0;      // give up after this distance

    /**
     * Handles bullet scatter effects that spawn multiple bullets in random directions
     */
    private void updateBulletScatter(BulletScatter bulletScatter) {
        entities.getFieldEffects().remove(bulletScatter.id());
        for (int i = 0; i < bulletScatter.getBulletCount(); i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
            double dirX = Math.cos(angle);
            double dirY = Math.sin(angle);

            Vector2D spawnPos = bulletScatter.position().add(new Vector2D(dirX * INITIAL_OFFSET, dirY * INITIAL_OFFSET));

            // Try to find a spawn position that isn't inside any obstacle
            boolean inside;
            double traveled = 0.0;
            do {
                Vector2D finalSpawn = spawnPos; // effectively final for lambda
                inside = entities.getObstacles().stream()
                        .anyMatch(obstacle -> CollisionUtils.checkCirclePolygonCollision(finalSpawn, SPAWN_CHECK_RADIUS, obstacle.vertices()));

                if (inside) {
                    // move outward along direction
                    spawnPos = spawnPos.add(new Vector2D(dirX * STEP, dirY * STEP));
                    traveled += STEP;
                }
            } while (inside && traveled < MAX_DISTANCE);

            if (inside) {
                // couldn't find a free spot; skip this scattered bullet
                continue;
            }

            double vx = dirX * bulletScatter.getBulletSpeed();
            double vy = dirY * bulletScatter.getBulletSpeed();

            Bullet scatteredBullet = new Bullet(
                    spawnPos.x(),
                    spawnPos.y(),
                    vx,
                    vy,
                    bulletScatter.getShooterId(),
                    bulletScatter.getTeam(),
                    bulletScatter.getBulletDamage(),
                    bulletScatter.getBulletSpeed(),
                    bulletScatter.getBulletRange(),
                    0.8,
                    null
            );

            entities.getBullets().add(scatteredBullet);
        }
    }

    private void placeLimitedEffect(FieldEffect fieldEffect, int max, Predicate<FieldEffect> matchOwner) {
        entities.getFieldEffects()
                .values()
                .stream()
                .filter(matchOwner)
                .sorted(Comparator.comparing(FieldEffect::timestamp).reversed())
                .map(FieldEffect::id)
                .skip(max - 1)
                .toList()
                .forEach(entities.getFieldEffects()::remove);
        addFieldEffect(fieldEffect);
    }
}
