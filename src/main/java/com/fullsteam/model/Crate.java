package com.fullsteam.model;

import java.util.UUID;

public class Crate {
    private final String id;
    private final String ownerId;
    private final double x;
    private final double y;
    private final double size;
    private int hp;
    private final int maxHp;

    public Crate(String ownerId, double x, double y, double size, int hp) {
        this.id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.x = x;
        this.y = y;
        this.size = size;
        this.hp = hp;
        this.maxHp = hp;
    }

    public String getId() {
        return id;
    }

    public String getOwnerId() {
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

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void takeDamage(int damage) {
        this.hp -= damage;
    }

    public boolean isDestroyed() {
        return this.hp <= 0;
    }
}
