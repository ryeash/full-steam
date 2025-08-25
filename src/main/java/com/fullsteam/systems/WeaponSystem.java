package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.BulletEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.HasLife;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.MountedWeapon;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
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
import static com.fullsteam.Config.PLAYER_RADIUS;
import static com.fullsteam.Config.TURRET_INACCURACY;

/**
 * Handles all weapon-related game logic including firing, bullet updates, and laser blasts.
 * Separated from AbstractGameStateManager to follow Single Responsibility Principle.
 */
public class WeaponSystem {

    private final GameEntities entities;
    private final Consumer<BulletEffect> bulletEffectHandler;
    private final BiConsumer<Player, Player> killPlayerHandler;

    public WeaponSystem(WeaponSystem other) {
        this.entities = other.entities;
        this.bulletEffectHandler = other.bulletEffectHandler;
        this.killPlayerHandler = other.killPlayerHandler;
    }

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
        double bulletX = player.getX();
        double bulletY = player.getY();

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
                entities.getLaserBlasts().add(laserBlast);
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
                entities.getBullets().add(bullet);
            }
        }
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
            entities.getBullets().add(bullet);
        }
        turret.shoot();
    }

    /**
     * Fires a vehicle-mounted weapon
     */
    public void fireVehicleWeapon(Vehicle vehicle, MountedWeapon mountedWeapon, Long playerId, PlayerInput input) {
        if (!mountedWeapon.canShoot()) {
            return;
        }

        Weapon weapon = mountedWeapon.getWeapon();
        Player controller = entities.getPlayer(playerId);
        if (controller == null) {
            return;
        }

        // Calculate desired weapon angle based on mouse input
        double desiredAngle;
        double dx = input.getMouseX() - mountedWeapon.position().x();
        double dy = input.getMouseY() - mountedWeapon.position().y();
        desiredAngle = Math.atan2(dy, dx);

        // Apply traverse constraints to get the final weapon angle
        double weaponAngle = mountedWeapon.getConstrainedAngle(desiredAngle, vehicle.getAngle());

        // Fire weapon
        int bulletsToFire = Math.min(weapon.getBulletsPerShot(), mountedWeapon.getCurrentAmmo());

        for (int i = 0; i < bulletsToFire; i++) {
            double spread = ThreadLocalRandom.current().nextGaussian() * (weapon.getBulletSpread() / 6.0);
            double finalAngle = weaponAngle + spread;

            if (weapon.getOrdinance() == Weapon.Ordinance.LASER) {
                // Create laser blast
                Vector2D start = mountedWeapon.position();
                Vector2D end = start.add(new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle)).multiply(weapon.getBulletRange()));
                LaserBlast laserBlast = new LaserBlast(
                        start,
                        end,
                        playerId,
                        controller.getTeam(),
                        weapon.getBulletDamage() * mountedWeapon.getDamageModification(),
                        System.currentTimeMillis() + 100);
                applyLaser(laserBlast);
                entities.getLaserBlasts().add(laserBlast);
            } else {
                // Create bullet
                Bullet bullet = new Bullet(
                        mountedWeapon.position().x(),
                        mountedWeapon.position().y(),
                        Math.cos(finalAngle),
                        Math.sin(finalAngle),
                        playerId,
                        controller.getTeam(),
                        weapon.getBulletDamage() * mountedWeapon.getDamageModification(),
                        weapon.getBulletSpeed(),
                        weapon.getBulletRange(),
                        weapon.getBulletSpeedDecay(),
                        weapon.getOnBulletDestruction());
                entities.getBullets().add(bullet);
            }
        }

        mountedWeapon.shoot();
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
                        if (!player.isDead() && player.getTeam() != bullet.getTeam()) {
                            Vector2D playerCenter = player.position();
                            // For the purposes of player collisions, we use a slightly larger radius to account fo the bullet not being a point.
                            if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, playerCenter, PLAYER_RADIUS + 2)) {
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

    /**
     * Updates laser blasts (removes expired ones)
     */
    public void updateLaserBlasts(long delta) {
        entities.getLaserBlasts().removeIf(LaserBlast::isExpired);
    }

    /**
     * Applies laser damage to targets
     */
    private void applyLaser(LaserBlast laserBlast) {
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
                            if (player.takeDamage(laserBlast.getDamage())) {
                                killPlayerHandler.accept(player, shooter);
                            }
                        }
                    }
                }
                case Turret turret -> {
                    if (turret.getTeam() != laserBlast.getTeam()) {
                        Vector2D position = turret.position();
                        if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), position, turret.getRadius())) {
                            // Apply damage
                            turret.takeDamage(laserBlast.getDamage());
                        }
                    }
                }
                case Vehicle vehicle -> {
                    if (vehicle.getTeam() != laserBlast.getTeam()) {
                        if (CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), vehicle)) {
                            // Apply damage
                            vehicle.takeDamage(laserBlast.getDamage());
                        }
                    }
                }
                case Obstacle o -> {
                    if (o instanceof HasLife hasLife && CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), o)) {
                        hasLife.takeDamage(laserBlast.getDamage());
                    }
                }
                case null, default -> {
                    // Ignore other types
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
