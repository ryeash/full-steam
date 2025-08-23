package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Represents a destructible base that Team 2 defends from Team 1's attacks.
 * The base is essentially a large, destructible obstacle with significant health.
 */
@Introspected
public class Base extends Obstacle implements HasId, HasLife, Targetable {
    @JsonIgnore
    private final long id;
    private final Vector2D center;
    private final double radius;
    private double hp;
    private final double maxHp;
    private final int team;

    public Base(long id, Vector2D center, double radius, double hp, int team) {
        super(generateBaseVertices(center, radius), true); // true = rendered
        this.id = id;
        this.center = center;
        this.radius = radius;
        this.hp = hp;
        this.maxHp = hp;
        this.team = team;
    }

    /**
     * Generates vertices for a large octagonal base structure.
     */
    private static List<Vector2D> generateBaseVertices(Vector2D center, double radius) {
        List<Vector2D> vertices = new java.util.ArrayList<>();
        int sides = 8; // Octagon for more interesting shape
        for (int i = 0; i < sides; i++) {
            double angle = 2 * Math.PI * i / sides;
            double x = center.x() + radius * Math.cos(angle);
            double y = center.y() + radius * Math.sin(angle);
            vertices.add(new Vector2D(x, y));
        }
        return vertices;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Vector2D position() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public int getTeam() {
        return team;
    }

    @Override
    public double getHp() {
        return hp;
    }

    @Override
    public double getMaxHp() {
        return maxHp;
    }

    @Override
    public void setHp(double hp) {
        this.hp = Math.max(0, Math.min(hp, maxHp));
    }

    @Override
    public boolean takeDamage(double damage) {
        this.hp -= damage;
        return this.hp <= 0;
    }

    public boolean isDestroyed() {
        return this.hp <= 0;
    }

    /**
     * Returns the percentage of health remaining (0.0 to 1.0)
     */
    public double getHealthPercentage() {
        return maxHp > 0 ? hp / maxHp : 0.0;
    }

    public double getX() {
        return center.x();
    }

    public double getY() {
        return center.y();
    }
}
