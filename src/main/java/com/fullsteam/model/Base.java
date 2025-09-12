package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Represents a destructible base that Team 2 defends from Team 1's attacks.
 * The base is essentially a large, destructible obstacle with significant health.
 */
@Introspected
public class Base extends Obstacle implements HasLife, Targetable, Objective {
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
    
    // Objective interface implementations
    
    @Override
    public int getOwningTeam() {
        return team;
    }
    
    @Override
    public int getControllingTeam() {
        return isDestroyed() ? 0 : team; // Team controls their base unless destroyed
    }
    
    @Override
    public boolean isContested() {
        // Bases could be considered contested if under heavy attack
        // For now, return false as bases don't have traditional contested state
        return false;
    }
    
    @Override
    public int getStrategicValue() {
        return 10; // Bases are the highest value objectives
    }
    
    @Override
    public double getInteractionRadius() {
        return radius * 1.5; // Slightly larger than the base itself for interaction
    }
    
    @Override
    public String getObjectiveType() {
        return "Base";
    }
    
    @Override
    public boolean canInteract(int teamId) {
        // Enemy teams can attack bases, friendly teams can defend
        return true;
    }
    
    @Override
    public int getPriorityForTeam(int teamId) {
        if (teamId == team) {
            // Defending our own base
            double healthRatio = hp / maxHp;
            if (healthRatio < 0.3) {
                return 9; // Critical - base almost destroyed
            } else if (healthRatio < 0.7) {
                return 6; // High priority - base damaged
            } else {
                return 3; // Low priority - base healthy
            }
        } else {
            // Attacking enemy base
            double healthRatio = hp / maxHp;
            if (healthRatio < 0.3) {
                return 10; // Very high priority - almost destroyed
            } else {
                return 8; // High priority - attack enemy base
            }
        }
    }
}
