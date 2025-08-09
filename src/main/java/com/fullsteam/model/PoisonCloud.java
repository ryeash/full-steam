package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

/**
 * Represents a lingering cloud that applies damage over time to players within its radius.
 * This is a type of {@link BulletEffect} that is created upon a bullet'''s destruction.
 */
public class PoisonCloud implements BulletEffect {
    private final long id;
    private final double x;
    private final double y;
    @JsonIgnore
    private final long shooterId;
    @JsonIgnore
    private final int team;
    private final double radius;
    @JsonIgnore
    private final double damagePerTick;
    private final long expiration;

    @JsonIgnore
    private transient long lastDamageTickTime;

    public PoisonCloud(double x, double y, long shooterId, int team, double radius, double damagePerTick, long duration) {
        this.id = Config.ID_COUNTER.incrementAndGet();
        this.x = x;
        this.y = y;
        this.shooterId = shooterId;
        this.team = team;
        this.radius = radius;
        this.damagePerTick = damagePerTick;
        this.expiration = System.currentTimeMillis() + duration;
        this.lastDamageTickTime = System.currentTimeMillis(); // Start ticking immediately
    }

    /**
     * A static factory method for creating a standard poison cloud from a bullet.
     */
    public static PoisonCloud create(Bullet bullet) {
        return new PoisonCloud(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                80,    // radius
                6,     // damage per tick
                5000); // 5 seconds duration
    }

    @Override
    public long id() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public long getShooterId() {
        return shooterId;
    }

    public int getTeam() {
        return team;
    }

    public double getRadius() {
        return radius;
    }

    public double getDamagePerTick() {
        return damagePerTick;
    }

    public long getExpiration() {
        return expiration;
    }

    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() > expiration;
    }

    @JsonIgnore
    public long getLastDamageTickTime() {
        return lastDamageTickTime;
    }

    /**
     * Updates the last time damage was applied by this cloud.
     * This is called by the server to manage the damage-over-time interval.
     */
    @JsonIgnore
    public void setLastDamageTickTime(long time) {
        this.lastDamageTickTime = time;
    }
}