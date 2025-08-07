package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents the location and lifetime of a visual marker for a player's death.
 *
 * @param x              The x-coordinate of the death location.
 * @param y              The y-coordinate of the death location.
 * @param expirationTime The system time (in ms) when this marker should be removed.
 */
public record DeathMarker(long id,
                          double x,
                          double y,
                          @JsonIgnore long expirationTime) implements HasId {
    @Override
    public long getId() {
        return 0;
    }
}