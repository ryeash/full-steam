package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

import static com.fullsteam.Config.ID_COUNTER;

@Introspected
public class LaserBlast implements HasId {
    private final long id = ID_COUNTER.incrementAndGet();
    private final Vector2D start;
    private  Vector2D end;
    private final int team;
    private final long shooterId;
    private final double damage;
    private final long expires;

    public LaserBlast(Vector2D start, Vector2D end, long shooterId, int team, double damage, long expires) {
        this.start = start;
        this.end = end;
        this.shooterId = shooterId;
        this.team = team;
        this.damage = damage;
        this.expires = expires;
    }

    @Override
    public long id() {
        return id;
    }

    public Vector2D getStart() {
        return start;
    }

    public Vector2D getEnd() {
        return end;
    }

    public void setEnd(Vector2D end) {
        this.end = end;
    }

    public int getTeam() {
        return team;
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getDamage() {
        return damage;
    }

    public long getExpires() {
        return expires;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expires;
    }
}
