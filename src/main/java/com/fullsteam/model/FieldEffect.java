package com.fullsteam.model;

public interface FieldEffect extends HasId {

    enum Type {
        EXPLOSION,
        POISON,
        SLOW,
        SMOKE
    }

    Type getType();

    /**
     * Returns the x-coordinate of the effect.
     *
     * @return The x-coordinate.
     */
    double getX();

    /**
     * Returns the y-coordinate of the effect.
     *
     * @return The y-coordinate.
     */
    double getY();

    /**
     * Returns the position of the effect as a Vector2D.
     *
     * @return The position vector.
     */
    default Vector2D position() {
        return new Vector2D(getX(), getY());
    }

    /**
     * Returns the radius of the effect.
     *
     * @return The radius.
     */
    double getRadius();

    /**
     * Returns the squared radius of the effect for performance optimization in distance calculations.
     *
     * @return The squared radius.
     */
    double getRadiusSquared();

    /**
     * Returns the team associated with this effect.
     *
     * @return The team identifier.
     */
    int getTeam();

    /**
     * Returns the expiration time of the effect in milliseconds since epoch.
     *
     * @return The expiration time.
     */
    long getExpiration();

    /**
     * Checks if the effect has expired based on the current system time.
     *
     * @return true if the effect is expired, false otherwise.
     */
    default boolean isExpired() {
        return System.currentTimeMillis() > getExpiration();
    }
}
