package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents a lingering cloud that applies damage over time to players within its radius.
 * This is a type of {@link BulletEffect} that is created upon a bullet'''s destruction.
 */
@Introspected
public class PoisonCloud extends AbstractFieldEffect implements BulletEffect {
    @JsonIgnore
    private final long shooterId;
    @JsonIgnore
    private final double damagePerTick;
    @JsonIgnore
    private transient long lastDamageTickTime;

    public PoisonCloud(double x, double y, long shooterId, int team, double radius, double damagePerTick, long duration) {
        super(Type.POISON, Config.ID_COUNTER.incrementAndGet(), x, y, radius, team, System.currentTimeMillis() + duration);
        this.shooterId = shooterId;
        this.damagePerTick = damagePerTick;
        this.lastDamageTickTime = System.currentTimeMillis(); // Start ticking immediately
    }

    /**
     * A static factory method for creating a standard poison cloud from a bullet.
     */
    public static PoisonCloud create(Bullet bullet, Object destructionSource) {
        return new PoisonCloud(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                80,    // radius
                8,     // damage per tick
                5000); // 5 seconds duration
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getDamagePerTick() {
        return damagePerTick;
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