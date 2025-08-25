package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import io.micronaut.core.annotation.Introspected;

import java.util.Optional;
import java.util.Set;

@Introspected
public class Turret implements HasId, BulletEffect, HasLife, Targetable {
    private final long id;
    @JsonIgnore
    private final long ownerId;
    private final int team;
    private final double x;
    private final double y;
    @JsonIgnore
    private final Vector2D position;
    private final double radius;
    @JsonIgnore
    private final Weapon weapon;
    private double angle;
    private double hp;
    private final double maxHp;
    @JsonIgnore
    private long nextShotTime;
    @JsonIgnore
    private int ammoInMag;
    @JsonIgnore
    private boolean reloading;
    @JsonIgnore
    private long reloadCompleteTime;

    /**
     * Represents a decision to fire the weapon in a specific direction.
     */
    public record ShootAction(double directionX, double directionY) {
    }

    public Turret(long id, long ownerId, int team, double x, double y, double radius, Weapon weapon, double angle) {
        this.id = id;
        this.ownerId = ownerId;
        this.team = team;
        this.x = x;
        this.y = y;
        this.position = new Vector2D(x, y);
        this.radius = radius;
        this.weapon = weapon;
        this.angle = angle;
        this.hp = Config.DEFAULT_PLAYER_HEALTH / 2;
        this.maxHp = this.hp;
        this.nextShotTime = 0;
        this.reloading = false;
        this.ammoInMag = weapon.getRoundsPerMagazine();
        this.reloadCompleteTime = 0;
    }

    public long id() {
        return id;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public int getTeam() {
        return team;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public Vector2D position() {
        return position;
    }

    public double getRadius() {
        return radius;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public double getHp() {
        return hp;
    }

    @Override
    public void setHp(double hp) {
        this.hp = hp;
    }

    @Override
    public double getMaxHp() {
        return maxHp;
    }

    @Override
    public boolean takeDamage(double damage) {
        this.hp -= damage;
        return this.hp <= 0;
    }

    @JsonIgnore
    public boolean isReloading() {
        return reloading;
    }

    public long getReloadCompleteTime() {
        return reloadCompleteTime;
    }

    public int getAmmoInMag() {
        return ammoInMag;
    }

    public void startReload() {
        if (reloading || ammoInMag == weapon.getRoundsPerMagazine()) {
            return;
        }
        reloading = true;
        reloadCompleteTime = System.currentTimeMillis() + weapon.getReloadTime();
    }

    public void finishReload() {
        reloading = false;
        ammoInMag = weapon.getRoundsPerMagazine();
    }

    public boolean canShoot() {
        return !reloading
                && ammoInMag > 0
                && System.currentTimeMillis() >= nextShotTime;
    }

    public void shoot() {
        if (!canShoot()) {
            return;
        }
        this.nextShotTime = System.currentTimeMillis() + weapon.getFireRateCooldown();
        this.ammoInMag -= weapon.getBulletsPerShot();
    }

    /**
     * The main update loop for the turret's AI.
     * It finds a target, aims, and decides whether to shoot or reload.
     *
     * @param gameState  The current state of the game.
     * @param playerGrid A spatial grid for efficient player lookups.
     * @return An Optional {@link ShootAction} if the turret decides to fire.
     */
    public Optional<ShootAction> update(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        // Handle reload completion
        if (isReloading() && System.currentTimeMillis() >= getReloadCompleteTime()) {
            finishReload();
        }
        // If no ammo left and not reloading, start reloading
        if (getAmmoInMag() <= 0 && !isReloading()) {
            startReload();
            return Optional.empty();
        }
        if (canShoot()) {
            Set<Targetable> nearby = playerGrid.getNearby(position(), weapon.getBulletRange());
            return findBestTarget(gameState, nearby)
                    .map(finalTarget -> {
                        Vector2D directionToTarget = finalTarget.position().subtract(position());
                        setAngle(Math.atan2(directionToTarget.y(), directionToTarget.x()));
                        return new ShootAction(directionToTarget.x(), directionToTarget.y());
                    });
        }
        return Optional.empty();
    }

    private Optional<Targetable> findBestTarget(GameState gameState, Set<Targetable> nearbyPlayers) {
        Targetable bestTarget = null;
        double minDistanceSq = Double.MAX_VALUE;
        double effectiveRange = getWeapon().getBulletRange() * 0.75;
        double rangeSq = effectiveRange * effectiveRange;
        for (Targetable p : nearbyPlayers) {
            double distanceSq;
            if (p instanceof Player player
                    && !player.isDead()
                    && player.getTeam() != getTeam()
                    && player.getInvisibilityEndTime() < System.currentTimeMillis()) {
                distanceSq = position().distanceSquared(p.position());
            } else if (p instanceof Turret turret
                    && turret.getTeam() != getTeam()) {
                distanceSq = position().distanceSquared(p.position());
            } else {
                continue; // Skip if not a valid target
            }
            if (distanceSq < rangeSq && distanceSq < minDistanceSq) {
                boolean isBlocked = gameState.obstacles().stream().anyMatch(obstacle -> CollisionUtils.checkLinePolygonCollision(position(), p.position(), obstacle));
                if (!isBlocked) {
                    minDistanceSq = distanceSq;
                    bestTarget = p;
                }
            }
        }
        return Optional.ofNullable(bestTarget);
    }

    public static Turret create(Bullet bullet, Object destructionSource) {
        return new Turret(Config.ID_COUNTER.incrementAndGet(),
                bullet.getShooterId(),
                bullet.getTeam(),
                bullet.getX(),
                bullet.getY(),
                Config.PLAYER_RADIUS,
                WeaponFactory.getDefaultWeapon(),
                0);
    }
}
