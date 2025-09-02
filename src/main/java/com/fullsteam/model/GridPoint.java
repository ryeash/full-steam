package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

@Introspected
public final class GridPoint extends AbstractFieldEffect implements BulletEffect, Targetable, HasLife {

    private double hp;
    private final double maxHp;
    @JsonIgnore
    private final long ownerId;
    @JsonIgnore
    private long lastLaserTime;

    public GridPoint(long id, int team, double x, double y, double radius, long expiration, long ownerId, double maxHp) {
        super(Type.GRID_POINT, id, x, y, radius, team, expiration);
        this.ownerId = ownerId;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.lastLaserTime = 0;
    }

    public long getOwnerId() {
        return ownerId;
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
    public Vector2D position() {
        return super.position();
    }

    @Override
    public boolean takeDamage(double damage) {
        this.hp -= damage;
        return this.hp <= 0;
    }

    @Override
    public boolean isExpired() {
        return hp <= 0;
    }

    public long getLastLaserTime() {
        return lastLaserTime;
    }

    public boolean readyToFire() {
        return System.currentTimeMillis() > lastLaserTime + Config.DEFENSE_GRID_LASER_COOLDOWN;
    }

    public void setLastLaserTime(long lastLaserTime) {
        this.lastLaserTime = lastLaserTime;
    }

    public static GridPoint create(Bullet bullet, Object destructionSource) {
        return new GridPoint(
                Config.ID_COUNTER.incrementAndGet(),
                bullet.getTeam(),
                bullet.getX(),
                bullet.getY(),
                Config.PLAYER_RADIUS / 2, // Small radius
                System.currentTimeMillis() + 30000, // 30 second duration
                bullet.getShooterId(),
                Config.DEFENSE_GRID_HEALTH // Low health - easy to destroy
        );
    }
}
