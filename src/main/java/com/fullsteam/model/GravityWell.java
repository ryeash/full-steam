package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

/**
 * Represents a gravity well that pulls players toward its center with increasing intensity.
 * Players closer to the center experience stronger gravitational pull.
 * This is a type of {@link BulletEffect} that is created upon a bullet's destruction.
 */
public class GravityWell extends AbstractFieldEffect implements BulletEffect {
    @JsonIgnore
    private final long shooterId;
    @JsonIgnore
    private final double maxGravityForce;
    @JsonIgnore
    private final double innerRadius; // Radius where max force is applied

    public GravityWell(double x, double y, long shooterId, int team, double radius, double maxGravityForce, double innerRadius, long duration) {
        super(FieldEffect.Type.GRAVITY_WELL, Config.ID_COUNTER.incrementAndGet(), x, y, radius, team, System.currentTimeMillis() + duration);
        this.shooterId = shooterId;
        this.maxGravityForce = maxGravityForce;
        this.innerRadius = innerRadius;
    }

    /**
     * A static factory method for creating a standard gravity well from a bullet.
     */
    public static GravityWell create(Bullet bullet, Object destructionSource) {
        return new GravityWell(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                120,   // radius - larger than poison cloud
                15, // max gravity force
                10.0,  // inner radius where max force applies
                8000); // 8 seconds duration
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getMaxGravityForce() {
        return maxGravityForce;
    }

    public double getInnerRadius() {
        return innerRadius;
    }

    /**
     * Calculates the gravity force at a given distance from the center.
     * Force increases as distance decreases, with maximum force applied within inner radius.
     *
     * @param distanceFromCenter Distance from the gravity well center
     * @return The gravity force to apply (0.0 to maxGravityForce)
     */
    public double calculateGravityForce(double distanceFromCenter) {
        if (distanceFromCenter >= getRadius()) {
            return 0.0; // Outside the gravity well
        }

        if (distanceFromCenter <= innerRadius) {
            return maxGravityForce; // Maximum force in the inner radius
        }

        // Linear interpolation between inner radius and outer radius
        double normalizedDistance = (distanceFromCenter - innerRadius) / (getRadius() - innerRadius);
        return maxGravityForce * (1.0 - normalizedDistance);
    }
}
