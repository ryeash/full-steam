package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

public class Explosion extends AbstractFieldEffect implements BulletEffect {

    @JsonIgnore
    private final long shooterId;
    @JsonIgnore
    private final double damage;
    @JsonIgnore
    private boolean damageApplied = false;

    public Explosion(double x, double y, long shooterId, int team, double size, double damage, long duration) {
        super(Type.EXPLOSION, Config.ID_COUNTER.incrementAndGet(), x, y, size, team, System.currentTimeMillis() + duration);
        this.shooterId = shooterId;
        this.damage = damage;
    }

    public static Explosion rocket(Bullet bullet, Object destructionSource) {
        return new Explosion(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                75, // size/radius
                100, // damage
                300); // duration ms
    }

    public static Explosion grenade(Bullet bullet, Object destructionSource) {
        return new Explosion(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                50, // size/radius
                75, // damage
                300); // duration ms
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getDamage() {
        return damage;
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
