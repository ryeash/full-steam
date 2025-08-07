package com.fullsteam.model;

import com.fullsteam.Config;

public class Crate implements HasId {
    private final long id;
    private final long ownerId;
    private final double x;
    private final double y;
    private final double size;
    private double hp;
    private final double maxHp;

    public Crate(long ownerId, double x, double y, double size, double hp) {
        this.id = Config.ID_COUNTER.incrementAndGet();
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.size = size;
        this.hp = hp;
        this.maxHp = hp;
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

    public double getSize() {
        return size;
    }

    public double getHp() {
        return hp;
    }

    public double getMaxHp() {
        return maxHp;
    }

    public void takeDamage(double damage) {
        this.hp -= damage;
    }

    public boolean isDestroyed() {
        return this.hp <= 0;
    }
}
