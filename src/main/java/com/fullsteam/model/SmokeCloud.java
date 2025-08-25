package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents a lingering cloud that applies damage over time to players within its radius.
 * This is a type of {@link BulletEffect} that is created upon a bullet'''s destruction.
 */
@Introspected
public class SmokeCloud extends AbstractFieldEffect implements BulletEffect {
    @JsonIgnore
    private final long shooterId;

    public SmokeCloud(double x, double y, long shooterId, int team, double radius, long duration) {
        super(Type.SMOKE, Config.ID_COUNTER.incrementAndGet(), x, y, radius, team, System.currentTimeMillis() + duration);
        this.shooterId = shooterId;
    }

    /**
     * A static factory method for creating a standard poison cloud from a bullet.
     */
    public static SmokeCloud create(Bullet bullet, Object destructionSource) {
        return new SmokeCloud(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                -1, // smoke applies to everyone, so no team restriction
                100,    // radius
                5000); // 5 seconds duration
    }

    public long getShooterId() {
        return shooterId;
    }
}