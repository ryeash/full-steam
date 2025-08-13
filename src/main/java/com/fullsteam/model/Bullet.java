package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Optional;
import java.util.function.BiFunction;

import static com.fullsteam.Config.ID_COUNTER;

public class Bullet implements HasId {
    private final long id = ID_COUNTER.incrementAndGet();
    private Vector2D position;
    private final Vector2D direction;
    private final int team;

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
    private final BiFunction<Bullet, Object, BulletEffect> onDestructionAction;

    public Bullet(double x, double y, double velocityX, double velocityY, long shooterId, int team, double damage, double speed, double range, double bulletSpeedDecay, BiFunction<Bullet, Object, BulletEffect> onDestructionAction) {
        this.position = new Vector2D(x, y);
        this.direction = new Vector2D(velocityX, velocityY).normalize();
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
        Vector2D start = position;
        this.position = position.add(direction.multiply(deltaSeconds * speed));
        // Apply speed decay
        speed *= Math.pow(bulletSpeedDecay, deltaSeconds);
        distanceTraveled += Math.sqrt(start.distanceSquared(position));
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
    public Optional<BiFunction<Bullet, Object, BulletEffect>> getOnDestructionAction() {
        return Optional.ofNullable(onDestructionAction);
    }

    @Override
    public long id() {
        return id;
    }

    public double getX() {
        return position.x();
    }

    public double getY() {
        return position.y();
    }

    public Vector2D position() {
        return position;
    }

    public Vector2D direction() {
        return direction;
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