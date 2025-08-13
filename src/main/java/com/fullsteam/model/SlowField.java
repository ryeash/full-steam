package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

public class SlowField extends AbstractFieldEffect implements BulletEffect {

    @JsonIgnore
    private final long shooterId;
    @JsonIgnore
    private final double slowFactor;

    public SlowField(double x, double y, long shooterId, int team, double size, double slowFactor, long duration) {
        super(Type.SLOW, Config.ID_COUNTER.incrementAndGet(), x, y, size, team, System.currentTimeMillis() + duration);
        this.shooterId = shooterId;
        this.slowFactor = slowFactor;
    }

    public static SlowField create(Bullet bullet, Object destructionSource) {
        return new SlowField(
                bullet.getX(),
                bullet.getY(),
                bullet.getShooterId(),
                bullet.getTeam(),
                75, // size/radius
                Config.SLOW_FIELD_FACTOR,
                7000); // duration ms
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getSlowFactor() {
        return slowFactor;
    }
}
