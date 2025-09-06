package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class AbstractFieldEffect implements FieldEffect {
    private final Type type;
    private final long id;
    private final double x;
    private final double y;
    private final double radius;
    @JsonIgnore
    private final double radiusSquared;
    private final int team;
    private final long expiration;
    private final long timestamp = System.currentTimeMillis();

    public AbstractFieldEffect(Type type, long id, double x, double y, double radius, int team, long expiration) {
        this.type = type;
        this.id = id;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.radiusSquared = radius * radius;
        this.team = team;
        this.expiration = expiration;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getRadius() {
        return radius;
    }

    @Override
    public double getRadiusSquared() {
        return radiusSquared;
    }

    @Override
    public int getTeam() {
        return team;
    }

    @Override
    public long getExpiration() {
        return expiration;
    }

    @Override
    public long timestamp() {
        return timestamp;
    }
}
