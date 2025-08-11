package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Optional;
import java.util.function.Function;

import static com.fullsteam.Config.ID_COUNTER;

public class Bullet implements HasId {
    private final long id = ID_COUNTER.incrementAndGet();
    private double x;
    private double y;
    private final int team;

    @JsonIgnore
    private final double velocityX;
    @JsonIgnore
    private final double velocityY;
    @JsonIgnore
    private final long shooterId;
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
    @JsonIgnore
    private final Function<Bullet, BulletEffect> onDestructionAction;

    public Bullet(double x, double y, double velocityX, double velocityY, long shooterId, int team, double damage, double speed, double range, double bulletSpeedDecay) {
        this(x, y, velocityX, velocityY, shooterId, team, damage, speed, range, bulletSpeedDecay, null);
    }

    public Bullet(double x, double y, double velocityX, double velocityY, long shooterId, int team, double damage, double speed, double range, double bulletSpeedDecay, Function<Bullet, BulletEffect> onDestructionAction) {
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
        this.onDestructionAction = onDestructionAction;
    }

    public void update(long delta) {
        double deltaSeconds = (double) delta / 1000.0;
        // Update position
        Vector2D start = new Vector2D(x, y);
        x += deltaSeconds * velocityX * speed;
        y += deltaSeconds * velocityY * speed;
        Vector2D end = new Vector2D(x, y);
        // Apply speed decay
        speed *= Math.pow(bulletSpeedDecay, deltaSeconds);
        distanceTraveled += Math.sqrt(start.distanceSquared(end));
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

    @JsonIgnore
    public Optional<Function<Bullet, BulletEffect>> getOnDestructionAction() {
        return Optional.ofNullable(onDestructionAction);
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

    public double getVelocityX() {
        return velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public long getShooterId() {
        return shooterId;
    }

    public int getTeam() {
        return team;
    }

    public double getDamage() {
        return damage;
    }

    public double getSpeed() {
        return speed;
    }
}