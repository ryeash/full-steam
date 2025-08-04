package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Bullet {
    private double x;
    private double y;
    private final int team;

    @JsonIgnore
    private final double velocityX;
    @JsonIgnore
    private final double velocityY;
    @JsonIgnore
    private final String shooterId;
    @JsonIgnore
    private final double damage;
    @JsonIgnore
    private double speed;
    @JsonIgnore
    private final double bulletSpeedDecay;
    @JsonIgnore
    private final double maxRange;
    @JsonIgnore
    private double distanceTraveled;

    public Bullet(double x, double y, double velocityX, double velocityY, String shooterId, int team, double damage, double speed, double range, double bulletSpeedDecay) {
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.shooterId = shooterId;
        this.team = team;
        this.damage = damage;
        this.speed = speed;
        this.maxRange = range;
        this.bulletSpeedDecay = bulletSpeedDecay;
        this.distanceTraveled = 0;
    }

    public void update() {
        x += velocityX * speed;
        y += velocityY * speed;
        distanceTraveled += speed;
        speed *= bulletSpeedDecay;
    }

    /**
     * Checks if the bullet has traveled beyond its maximum allowed distance.
     *
     * @return true if the bullet should be removed, false otherwise.
     */
    @JsonIgnore
    public boolean hasExceededMaxDistance() {
        return distanceTraveled >= maxRange || speed < .25;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public String getShooterId() {
        return shooterId;
    }

    public int getTeam() {
        return team;
    }

    public double getDamage() {
        return damage;
    }
}