package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;

import java.util.Optional;
import java.util.Set;

public class Turret implements HasId, BulletEffect, HasLife, Targetable {
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

    /**
     * Internal record to hold a potential target and its priority score.
     */
    private record TargetInfo(Object target, double score) {
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

    @Override
    public Vector2D position() {
        return new Vector2D(x, y);
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
    public Optional<ShootAction> update(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        // Handle reload completion
        if (isReloading() && System.currentTimeMillis() >= getReloadCompleteTime()) {
            finishReload();
            return Optional.empty();
        }

        // Find the best target
        Optional<TargetInfo> bestTargetInfo = findBestTarget(gameState, playerGrid);

        if (bestTargetInfo.isPresent()) {
            Object finalTarget = bestTargetInfo.get().target();
            Vector2D directionToTarget;
            Vector2D turretPos = new Vector2D(getX(), getY());

            if (finalTarget instanceof Player p) {
                directionToTarget = p.position().subtract(turretPos);
            } else if (finalTarget instanceof Turret t) {
                directionToTarget = new Vector2D(t.getX(), t.getY()).subtract(turretPos);
            } else {
                return Optional.empty(); // Should not happen
            }

            // Aim at the target
            setAngle(Math.atan2(directionToTarget.y(), directionToTarget.x()));

            // If we can shoot, return a shoot action
            if (canShoot()) {
                return Optional.of(new ShootAction(directionToTarget.x(), directionToTarget.y()));
            }
        }

        // If we have no ammo and are not already reloading, start reloading.
        if (getCurrentAmmoInMagazine() <= 0 && !isReloading()) {
            startReload();
        }

        return Optional.empty();
    }

    private Optional<TargetInfo> findBestTarget(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        Player bestPlayer = findBestPlayerTarget(gameState, playerGrid);
        Turret bestTurret = findBestTurretTarget(gameState);

        TargetInfo playerTargetInfo = null;
        if (bestPlayer != null) {
            // Simple distance-based score for players
            double score = new Vector2D(getX(), getY()).distanceSquared(bestPlayer.position());
            playerTargetInfo = new TargetInfo(bestPlayer, score);
        }

        TargetInfo turretTargetInfo = null;
        if (bestTurret != null) {
            // Turrets are static threats. Prioritize them slightly over players at the same distance.
            double score = new Vector2D(getX(), getY()).distanceSquared(new Vector2D(bestTurret.getX(), bestTurret.getY())) * 0.9; // 10% score reduction to prioritize
            turretTargetInfo = new TargetInfo(bestTurret, score);
        }

        if (playerTargetInfo == null) {
            return Optional.ofNullable(turretTargetInfo);
        }
        if (turretTargetInfo == null) {
            return Optional.of(playerTargetInfo);
        }

        // Return the target with the lower (better) score
        return playerTargetInfo.score() < turretTargetInfo.score() ? Optional.of(playerTargetInfo) : Optional.of(turretTargetInfo);
    }

    private Player findBestPlayerTarget(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        Targetable bestTarget = null;
        double minDistanceSq = Double.MAX_VALUE;
        Player owner = gameState.players().stream().filter(p -> p.getId() == getOwnerId()).findFirst().orElse(null);
        if (owner == null) {
            return null;
        }
        double effectiveRange = getWeapon().getBulletRange() * 0.75;
        double rangeSq = effectiveRange * effectiveRange;
        Vector2D turretPos = new Vector2D(getX(), getY());
        Set<Targetable> nearbyPlayers = playerGrid.getNearby(getX() - getWeapon().getBulletRange(), getY() - getWeapon().getBulletRange(), getWeapon().getBulletRange() * 2, getWeapon().getBulletRange() * 2);
        for (Targetable p : nearbyPlayers) {
            if (!(p instanceof Player player)) {
                continue;
            }
            if (player.isDead() || player.getTeam() == owner.getTeam()) {
                continue;
            }
            double distSq = turretPos.distanceSquared(p.position());
            if (distSq < rangeSq && distSq < minDistanceSq) {
                // TODO: broadphase / narrowphase
                boolean isBlocked = gameState.obstacles().stream().anyMatch(obstacle -> CollisionUtils.checkLinePolygonCollision(turretPos, p.position(), obstacle.vertices()));
                if (!isBlocked) {
                    minDistanceSq = distSq;
                    bestTarget = p;
                }
            }
        }
        return (Player) bestTarget;
    }

    private Turret findBestTurretTarget(GameState gameState) {
        Turret bestTarget = null;
        double minDistanceSq = Double.MAX_VALUE;

        double effectiveRange = getWeapon().getBulletRange() * 0.75;
        double rangeSq = effectiveRange * effectiveRange;
        Vector2D turretPos = new Vector2D(getX(), getY());

        for (Turret otherTurret : gameState.turrets()) {
            if (otherTurret.getId() == this.id() || otherTurret.getTeam() == this.getTeam()) {
                continue;
            }

            Vector2D otherTurretPos = new Vector2D(otherTurret.getX(), otherTurret.getY());
            double distSq = turretPos.distanceSquared(otherTurretPos);

            if (distSq < rangeSq && distSq < minDistanceSq) {
                boolean isBlocked = gameState.obstacles().stream()
                        .anyMatch(obstacle -> CollisionUtils.checkLinePolygonCollision(turretPos, otherTurretPos, obstacle.vertices()));
                if (!isBlocked) {
                    minDistanceSq = distSq;
                    bestTarget = otherTurret;
                }
            }
        }
        return bestTarget;
    }

    public static Turret create(Bullet bullet) {
        return new Turret(Config.ID_COUNTER.incrementAndGet(), bullet.getShooterId(), bullet.getTeam(), bullet.getX(), bullet.getY(), Config.PLAYER_RADIUS, WeaponFactory.getDefaultWeapon(), 0);
    }
}
