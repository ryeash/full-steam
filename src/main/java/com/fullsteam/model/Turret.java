package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;

import java.util.Optional;
import java.util.Set;

public class Turret implements HasId, BulletEffect {
    private final long id;
    private final long ownerId;
    private final int team;
    private final double x;
    private final double y;
    private final double radius;
    private final Weapon weapon;
    private double angle;
    private double hp;
    private final double maxHp;
    @JsonIgnore
    private long nextShotTime;
    private int currentAmmoInMagazine;
    private boolean reloading;
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
        this.radius = radius;
        this.weapon = weapon;
        this.angle = angle;
        this.hp = Config.DEFAULT_PLAYER_HEALTH / 2;
        this.maxHp = this.hp;
        this.nextShotTime = 0;
        this.reloading = false;
        this.currentAmmoInMagazine = weapon.getRoundsPerMagazine();
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

    public double getHp() {
        return hp;
    }

    public void setHp(double hp) {
        this.hp = hp;
    }

    public double getMaxHp() {
        return maxHp;
    }

    @JsonIgnore
    public boolean isReloading() {
        return reloading;
    }

    public long getReloadCompleteTime() {
        return reloadCompleteTime;
    }

    public int getCurrentAmmoInMagazine() {
        return currentAmmoInMagazine;
    }

    public void startReload() {
        if (reloading || currentAmmoInMagazine == weapon.getRoundsPerMagazine()) {
            return;
        }
        reloading = true;
        reloadCompleteTime = System.currentTimeMillis() + weapon.getReloadTime();
    }

    public void finishReload() {
        reloading = false;
        currentAmmoInMagazine = weapon.getRoundsPerMagazine();
    }

    public boolean canShoot() {
        return !reloading
               && currentAmmoInMagazine > 0
               && System.currentTimeMillis() >= nextShotTime;
    }

    public void shoot() {
        if (!canShoot()) {
            return;
        }
        this.nextShotTime = System.currentTimeMillis() + weapon.getFireRateCooldown();
        this.currentAmmoInMagazine -= weapon.getBulletsPerShot();
    }

    /**
     * The main update loop for the turret's AI.
     * It finds a target, aims, and decides whether to shoot or reload.
     *
     * @param gameState  The current state of the game.
     * @param playerGrid A spatial grid for efficient player lookups.
     * @return An Optional {@link ShootAction} if the turret decides to fire.
     */
    public Optional<ShootAction> update(GameState gameState, SpatialGrid<Player> playerGrid) {
        // Handle reload completion
        if (isReloading() && System.currentTimeMillis() >= getReloadCompleteTime()) {
            finishReload();
            return Optional.empty();
        }

        // Find the best target
        Player target = findBestTarget(gameState, playerGrid);

        if (target != null) {
            // Aim at the target
            Vector2D turretPos = new Vector2D(getX(), getY());
            Vector2D targetPos = target.getCenter();
            Vector2D direction = targetPos.subtract(turretPos);
            setAngle(Math.atan2(direction.y(), direction.x()));

            // If we can shoot, return a shoot action
            if (canShoot()) {
                return Optional.of(new ShootAction(direction.x(), direction.y()));
            }
        }

        // If we have no ammo and are not already reloading, start reloading.
        if (getCurrentAmmoInMagazine() <= 0 && !isReloading()) {
            startReload();
        }

        return Optional.empty();
    }

    private Player findBestTarget(GameState gameState, SpatialGrid<Player> playerGrid) {
        Player bestTarget = null;
        double minDistanceSq = Double.MAX_VALUE;
        Player owner = gameState.players().stream().filter(p -> p.getId() == getOwnerId()).findFirst().orElse(null);
        if (owner == null) {
            return null;
        }
        double effectiveRange = getWeapon().getBulletRange() * 0.75;
        double rangeSq = effectiveRange * effectiveRange;
        Vector2D turretPos = new Vector2D(getX(), getY());
        Set<Player> nearbyPlayers = playerGrid.getNearby(getX() - getWeapon().getBulletRange(), getY() - getWeapon().getBulletRange(), getWeapon().getBulletRange() * 2, getWeapon().getBulletRange() * 2);
        for (Player p : nearbyPlayers) {
            if (p.isDead() || p.getTeam() == owner.getTeam()) continue;
            double distSq = turretPos.distanceSquared(p.getCenter());
            if (distSq < rangeSq && distSq < minDistanceSq) {
                boolean isBlocked = gameState.obstacles().stream().anyMatch(obstacle -> CollisionUtils.checkLinePolygonCollision(turretPos, p.getCenter(), obstacle.vertices()));
                if (!isBlocked) {
                    minDistanceSq = distSq;
                    bestTarget = p;
                }
            }
        }
        return bestTarget;
    }

    public static Turret create(Bullet bullet) {
        return new Turret(Config.ID_COUNTER.incrementAndGet(), bullet.getShooterId(), bullet.getTeam(), bullet.getX(), bullet.getY(), Config.PLAYER_SIZE / 2, WeaponFactory.getDefaultWeapon(), 0);
    }
}
