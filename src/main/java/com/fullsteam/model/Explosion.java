package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

public class Explosion implements BulletEffect {
    private final long id;
    private final double x;
    private final double y;
    @JsonIgnore
    private final long shooterId;
    @JsonIgnore
    private final int team;
    private final double size;
    @JsonIgnore
    private final double damage;
    private final long expiration;

    @JsonIgnore
    private boolean damageApplied = false;

    public Explosion(double x, double y, long shooterId, int team, double size, double damage, long duration) {
        this.id = Config.ID_COUNTER.incrementAndGet();
        this.x = x;
        this.y = y;
        this.shooterId = shooterId;
        this.team = team;
        this.size = size;
        this.damage = damage;
        this.expiration = System.currentTimeMillis() + duration;
    }

    public static Explosion rocket(Bullet bullet) {
        return new Explosion(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                75, // size/radius
                100, // damage
                300); // duration ms
    }

    public static Explosion grenade(Bullet bullet) {
        return new Explosion(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                50, // size/radius
                75, // damage
                300); // duration ms
    }

    @Override
    public long id() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public long getShooterId() {
        return shooterId;
    }

    public int getTeam() {
        return team;
    }

    public double getSize() {
        return size;
    }

    public double getDamage() {
        return damage;
    }

    public long getExpiration() {
        return expiration;
    }

    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() > expiration;
    }

    @JsonIgnore
    public boolean hasDamageBeenApplied() {
        return damageApplied;
    }

    @JsonIgnore
    public void markDamageApplied() {
        this.damageApplied = true;
    }
}
