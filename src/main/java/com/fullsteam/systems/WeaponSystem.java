package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.Base;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GridPoint;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.MountedWeapon;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.Weapon;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.LASER_SHOT_DURATION;
import static com.fullsteam.Config.PLAYER_RADIUS;

/**
 * Handles all weapon-related game logic including firing, bullet updates, and laser blasts.
 * Separated from AbstractGameStateManager to follow Single Responsibility Principle.
 */
public class WeaponSystem {

    private final GameEntities entities;
    private final Consumer<BulletEffect> bulletEffectHandler;
    private final BiConsumer<Player, Player> killPlayerHandler;

    public WeaponSystem(GameEntities entities, Consumer<BulletEffect> bulletEffectHandler, BiConsumer<Player, Player> killPlayerHandler) {
        this.entities = entities;
        this.bulletEffectHandler = bulletEffectHandler;
        this.killPlayerHandler = killPlayerHandler;
    }

    /**
     * Fires a weapon for a player at the specified angle
     */
    public void fireWeapon(Player player, double aimAngle) {
        if (!player.canShoot()) {
            return;
        }
        Weapon weapon = player.getWeapon();
        fireWeaponCommon(player, player.position(), weapon, player.getDamageMultiplier(), aimAngle);
        player.shoot();
    }

    /**
     * Fires a turret weapon at the specified angle
     */
    public void fireTurretWeapon(Turret turret, double aimAngle) {
        Weapon weapon = turret.getWeapon();
        Player owner = entities.getPlayer(turret.getOwnerId());
        if (owner == null) {
            return;
        }
        fireWeaponCommon(owner, turret.position(), weapon, 1.0, aimAngle);
        turret.shoot();
    }

    /**
     * Fires a vehicle-mounted weapon
     */
    public void fireVehicleWeapon(MountedWeapon mountedWeapon, Long playerId) {
        if (!mountedWeapon.canShoot()) {
            return;
        }
        Weapon weapon = mountedWeapon.getWeapon();
        Player controller = entities.getPlayer(playerId);
        if (controller == null) {
            return;
        }
        // Use the current weapon angle that has already been updated by VehicleManager
        // This ensures consistency between aiming and firing
        double weaponAngle = mountedWeapon.getCurrentAngle();
        fireWeaponCommon(controller, mountedWeapon.position(), weapon, mountedWeapon.getDamageModification(), weaponAngle);
        mountedWeapon.shoot();
    }

    private void fireWeaponCommon(Player player, Vector2D position, Weapon weapon, double damageModifier, double aimAngle) {
        double bulletX = position.x();
        double bulletY = position.y();

        // Fire all bullets for this shot (or whatever is left in the magazine)
        int bulletsToFire = Math.min(weapon.getBulletsPerShot(), player.getAmmoInMag());

        for (int i = 0; i < bulletsToFire; i++) {
            // Apply random spread to each bullet individually
            double spread = ThreadLocalRandom.current().nextGaussian() * (weapon.getBulletSpread() / 6.0); // 6.0 is 3 standard deviations on each side
            double finalAngle = aimAngle + spread;

            // For multi-bullet shots (like shotguns), add a slight positional stagger
            // so they don't all originate from the exact same pixel. This creates a more natural "spread".
            double finalX = bulletX;
            double finalY = bulletY;
            if (i > 1) {
                double staggerRadius = 4.0; // Max offset in pixels
                finalX += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * staggerRadius;
                finalY += (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * staggerRadius;
            }

            if (weapon.getOrdinance() == Weapon.Ordinance.LASER) {
                // For laser weapons, create a laser blast instead of a bullet
                // laser blasts are hit-scan/instantaneous, so no speed or range decay
                Vector2D start = new Vector2D(finalX, finalY);
                Vector2D end = start.add(new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle)).multiply(weapon.getBulletRange()));
                double laserDamageOverTimeMod = (double) 1000 / LASER_SHOT_DURATION;
                LaserBlast laserBlast = new LaserBlast(
                        start,
                        end,
                        player.getId(),
                        player.getTeam(),
                        (weapon.getBulletDamage() * laserDamageOverTimeMod) * damageModifier,
                        System.currentTimeMillis() + LASER_SHOT_DURATION);
                calculateTerminus(laserBlast);
                entities.getLaserBlasts().add(laserBlast);
            } else {
                Bullet bullet = new Bullet(
                        finalX,
                        finalY,
                        Math.cos(finalAngle),
                        Math.sin(finalAngle),
                        player.getId(),
                        player.getTeam(),
                        weapon.getBulletDamage() * damageModifier,
                        weapon.getBulletSpeed(),
                        weapon.getBulletRange(),
                        weapon.getBulletSpeedDecay(),
                        weapon.getOnBulletDestruction());
                entities.getBullets().add(bullet);
            }
        }
    }

    public void updateOrdinance(long delta) {
        updateBullets(delta);
        updateLaserBlasts(delta);
        entities.getLaserBlasts().removeIf(LaserBlast::isExpired);
    }

    /**
     * Updates all bullets and handles collisions
     */
    public void updateBullets(long delta) {
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
                        // For the purposes of player collisions, we use a slightly larger radius to account fo the bullet not being a point.
                        if (!player.isDead() && player.getTeam() != bullet.getTeam()
                            && CollisionUtils.checkLineCircleCollision(oldPos, newPos, player.position(), PLAYER_RADIUS + 2)) {
                            Player shooter = entities.getPlayer(bullet.getShooterId());

                            // Apply damage and check if it was a kill
                            if (player.takeDamage(bullet.getDamage())) {
                                // Use callback to handle kill (AbstractGameStateManager will handle the actual kill logic)
                                killPlayerHandler.accept(player, shooter);
                            }
                            applyBulletDestructionEffect(bullet, player);
                            return true; // Remove bullet on hit
                        }
                    }
                    case Turret turret -> {
                        if (turret.getTeam() != bullet.getTeam() && CollisionUtils.checkLineCircleCollision(oldPos, newPos, turret.position(), turret.getRadius())) {
                            // Apply damage and check if it was a kill
                            turret.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, target);
                            return true; // Remove bullet on hit
                        }
                    }
                    case GridPoint gridPoint -> {
                        if (gridPoint.getTeam() != bullet.getTeam() && CollisionUtils.checkLineCircleCollision(oldPos, newPos, gridPoint.position(), gridPoint.getRadius())) {
                            // Apply damage and check if it was a kill
                            gridPoint.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, target);
                            return true; // Remove bullet on hit
                        }
                    }
                    case Vehicle vehicle -> {
                        if (vehicle.getTeam() >= 0 && vehicle.getTeam() != bullet.getTeam() && CollisionUtils.checkLinePolygonCollision(oldPos, newPos, vehicle)) {
                            vehicle.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, target);
                            return true;
                        }
                    }
                    case Base base -> {
                        // Check for collision with enemy base only (prevent friendly fire)
                        if (base.getTeam() != bullet.getTeam() && CollisionUtils.checkLinePolygonCollision(oldPos, newPos, base)) {
                            base.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, base);
                            return true;
                        }
                    }
                    case Obstacle o -> {
                        if (o instanceof HasLife hasLife && CollisionUtils.checkLinePolygonCollision(oldPos, newPos, o)) {
                            hasLife.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, o);
                            return true;
                        }
                    }
                    case null, default -> throw new UnsupportedOperationException();
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

    private void updateLaserBlasts(long delta) {
        for (LaserBlast laserBlast : entities.getLaserBlasts()) {
            // laser blasts do damage-per-second
            double damageForDelta = laserBlast.getDamage() * ((double) delta / 1000);

            // Check collisions using spatial grid
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
                                if (player.takeDamage(damageForDelta)) {
                                    killPlayerHandler.accept(player, shooter);
                                }
                            }
                        }
                    }
                    case Turret turret -> {
                        if (turret.getTeam() != laserBlast.getTeam()) {
                            Vector2D position = turret.position();
                            if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), position, turret.getRadius())) {
                                turret.takeDamage(damageForDelta);
                            }
                        }
                    }
                    case GridPoint gridPoint -> {
                        if (gridPoint.getTeam() != laserBlast.getTeam()) {
                            Vector2D position = gridPoint.position();
                            if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), position, gridPoint.getRadius())) {
                                gridPoint.takeDamage(damageForDelta);
                            }
                        }
                    }
                    case Vehicle vehicle -> {
                        if (vehicle.getTeam() >= 0 && vehicle.getTeam() != laserBlast.getTeam()) {
                            if (CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), vehicle)) {
                                vehicle.takeDamage(damageForDelta);
                            }
                        }
                    }
                    case Base base -> {
                        // Check for collision with enemy base only (prevent friendly fire)
                        if (base.getTeam() != laserBlast.getTeam() && CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), base)) {
                            base.takeDamage(damageForDelta);
                        }
                    }
                    case Obstacle o -> {
                        if (o instanceof HasLife hasLife && CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), o)) {
                            hasLife.takeDamage(damageForDelta);
                        }
                    }
                    case null, default -> {
                        // Ignore other types
                    }
                }
            }
        }
    }

    /**
     * Applies laser damage to targets
     */
    public void calculateTerminus(LaserBlast laserBlast) {
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
    }

    /**
     * Applies bullet destruction effects
     */
    private void applyBulletDestructionEffect(Bullet bullet, Object destructionSource) {
        bullet.getOnDestructionAction()
                .map(action -> action.apply(bullet, destructionSource))
                .ifPresent(bulletEffectHandler);
    }
}
