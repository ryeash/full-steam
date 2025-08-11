package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

import java.util.List;

public class Crate implements HasId, HasLife, Targetable {
    private final long id;
    @JsonIgnore
    private final long ownerId;
    private final double x;
    private final double y;
    private final double size;
    private double hp;
    private final double maxHp;
    @JsonIgnore
    private final List<Vector2D> vertices;

    public Crate(long ownerId, double x, double y, double size, double hp) {
        this.id = Config.ID_COUNTER.incrementAndGet();
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.size = size;
        this.hp = hp;
        this.maxHp = hp;

        Vector2D topLeft = new Vector2D(x, y);
        Vector2D topRight = new Vector2D(x + size, y);
        Vector2D bottomLeft = new Vector2D(x, y + size);
        Vector2D bottomRight = new Vector2D(x + size, y + size);
        this.vertices = List.of(topLeft, topRight, bottomRight, bottomLeft);
    }

    @Override
    public long id() {
        return id;
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

    public List<Vector2D> getVertices() {
        return vertices;
    }
}
