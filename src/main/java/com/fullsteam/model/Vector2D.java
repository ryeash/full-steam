package com.fullsteam.model;

/**
 * A simple, immutable record to represent a 2D vector or point.
 * Using a record provides constructors, getters, equals(), hashCode(),
 * and toString() automatically.
 */
public record Vector2D(double x, double y) {

    public static final Vector2D ZERO = new Vector2D(0, 0);

    /**
     * Adds another vector to this vector.
     *
     * @param other The vector to add.
     * @return A new Vector2D representing the sum.
     */
    public Vector2D add(Vector2D other) {
        return new Vector2D(this.x + other.x, this.y + other.y);
    }

    /**
     * Multiplies this vector by a scalar value.
     * An alias for the scale() method for semantic clarity in different contexts.
     *
     * @param scalar The value to multiply the vector by.
     * @return A new, scaled Vector2D.
     */
    public Vector2D multiply(double scalar) {
        return new Vector2D(this.x * scalar, this.y * scalar);
    }

    /**
     * Calculates the squared magnitude (length) of this vector.
     * This is faster than magnitude() as it avoids a square root operation.
     *
     * @return The squared magnitude of the vector.
     */
    public double magnitudeSq() {
        return x * x + y * y;
    }

    /**
     * Calculates the magnitude (length) of this vector.
     *
     * @return The magnitude of the vector.
     */
    public double magnitude() {
        return Math.sqrt(magnitudeSq());
    }

    /**
     * Returns a new vector with the same direction but a magnitude of 1.
     * If the vector has a magnitude of 0, it returns a zero vector to prevent division by zero errors.
     *
     * @return A new, normalized Vector2D.
     */
    public Vector2D normalize() {
        double mag = magnitude();
        if (mag > 1e-9) { // Use a small epsilon to avoid floating point issues
            return new Vector2D(x / mag, y / mag);
        }
        return ZERO;
    }

    /**
     * Calculates the squared Euclidean distance between this vector and another.
     * This is faster than distance() as it avoids a square root operation,
     * making it ideal for distance comparisons.
     *
     * @param other The other vector.
     * @return The squared distance between the two vectors.
     */
    public double distanceSq(Vector2D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return dx * dx + dy * dy;
    }

    /**
     * Calculates the actual Euclidean distance between this vector and another.
     *
     * @param other The other vector.
     * @return The distance between the two vectors.
     */
    public double distance(Vector2D other) {
        return Math.sqrt(this.distanceSq(other));
    }

    /**
     * A static helper to calculate the squared Euclidean distance between two vectors.
     *
     * @param v1 The first vector.
     * @param v2 The second vector.
     * @return The squared distance between the two vectors.
     */
    public static double distanceSq(Vector2D v1, Vector2D v2) {
        double dx = v1.x() - v2.x();
        double dy = v1.y() - v2.y();
        return dx * dx + dy * dy;
    }

    /**
     * A static helper to calculate the actual Euclidean distance between two vectors.
     *
     * @param v1 The first vector.
     * @param v2 The second vector.
     * @return The distance between the two vectors.
     */
    public static double distance(Vector2D v1, Vector2D v2) {
        return Math.sqrt(distanceSq(v1, v2));
    }

    public Vector2D rotate(double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double newX = this.x * cos - this.y * sin;
        double newY = this.x * sin + this.y * cos;
        return new Vector2D(newX, newY);
    }
}