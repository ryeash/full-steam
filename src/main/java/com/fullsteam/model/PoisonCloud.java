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
    private final double damagePerSecond;

    public PoisonCloud(double x, double y, long shooterId, int team, double radius, double damagePerSecond, long duration) {
        super(Type.POISON, Config.ID_COUNTER.incrementAndGet(), x, y, radius, team, System.currentTimeMillis() + duration);
        this.shooterId = shooterId;
        this.damagePerSecond = damagePerSecond;
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
                16.0,  // damage per second (8 damage per tick * 2 ticks per second = 16 DPS)
                5000); // 5 seconds duration
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getDamagePerSecond() {
        return damagePerSecond;
    }

    /**
     * Calculates the damage to apply based on the time delta.
     * @param delta Time elapsed in milliseconds
     * @return Damage to apply for this time period
     */
    public double getDamage(long delta) {
        return damagePerSecond * (delta / 1000.0);
    }
}