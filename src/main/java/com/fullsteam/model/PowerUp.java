package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

import static com.fullsteam.Config.ID_COUNTER;

@Introspected
public class PowerUp implements HasId {
    public final long id = ID_COUNTER.incrementAndGet();
    public final Vector2D position;
    public final PowerUpType type;

    public PowerUp(Vector2D position, PowerUpType type) {
        this.position = position;
        this.type = type;
    }

    @Override
    public long id() {
        return id;
    }

    public String getIcon() {
        return type.icon;
    }

    public Vector2D getPosition() {
        return position;
    }

    public PowerUpType getType() {
        return type;
    }
}
