package com.fullsteam.model;

public class PowerUp {
    public final Vector2D position;
    public final PowerUpType type;

    public PowerUp(Vector2D position, PowerUpType type) {
        this.position = position;
        this.type = type;
    }

    public Vector2D getPosition() {
        return position;
    }

    public PowerUpType getType() {
        return type;
    }
}
