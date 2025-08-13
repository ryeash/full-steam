package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class Crate extends Obstacle implements HasId, HasLife, Targetable {
    @JsonIgnore
    private final long ownerId;
    private final double x;
    private final double y;
    private final double size;
    private double hp;
    private final double maxHp;

    public Crate(long ownerId, double x, double y, double size, double hp) {
        super(List.of(
                new Vector2D(x, y), // top left
                new Vector2D(x + size, y), // top right
                new Vector2D(x + size, y + size), // bottom right
                new Vector2D(x, y + size) // bottom left
        ), false);
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.size = size;
        this.hp = hp;
        this.maxHp = hp;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    @Override
    public Vector2D position() {
        return new Vector2D(x, y);
    }

    public double getSize() {
        return size;
    }

    @Override
    public double getHp() {
        return hp;
    }

    @Override
    public void setHp(double hp) {
        this.hp = hp;
    }

    public double getMaxHp() {
        return maxHp;
    }

    public boolean takeDamage(double damage) {
        this.hp -= damage;
        return this.hp <= 0;
    }

    public boolean isDestroyed() {
        return this.hp <= 0;
    }
}
