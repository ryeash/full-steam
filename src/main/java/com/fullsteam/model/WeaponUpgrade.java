package com.fullsteam.model;

import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents a weapon dropped by a defeated player that can be picked up by other players.
 */
@Introspected
public class WeaponUpgrade implements HasId {
    private final long id;
    private final Vector2D position;
    private final double radius;
    private final long expirationTime;

    public WeaponUpgrade(Vector2D position) {
        this.id = Config.ID_COUNTER.incrementAndGet();
        this.position = position;
        this.radius = 30.0;
        this.expirationTime = System.currentTimeMillis() + Config.RESPAWN_DELAY_MS;
    }

    @Override
    public long id() {
        return id;
    }

    public Vector2D getPosition() {
        return position;
    }

    public double getRadius() {
        return radius;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }

    public double getX() {
        return position.x();
    }

    public double getY() {
        return position.y();
    }

    /**
     * Returns the remaining time before this drop expires (in seconds)
     */
    public long getTimeRemaining() {
        return Math.max(0, (expirationTime - System.currentTimeMillis()) / 1000);
    }
}
