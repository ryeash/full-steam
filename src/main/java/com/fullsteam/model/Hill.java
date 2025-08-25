package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents the state of the capture point ("the hill") in King of the Hill.
 *
 * @param position        The center coordinates of the hill.
 * @param radius          The radius of the capture zone.
 * @param controllingTeam The team currently in control (0=neutral, 1=team1, 2=team2).
 * @param contested       True if players from both teams are on the hill.
 */
@Introspected
public record Hill(
        Vector2D position,
        double radius,
        @JsonIgnore double radiusSq,
        int controllingTeam,
        boolean contested
) {
    /**
     * Returns a new Hill instance with an updated state.
     * This is used to maintain immutability.
     */
    public Hill withState(int newControllingTeam, boolean newContested) {
        return new Hill(this.position, this.radius, this.radiusSq, newControllingTeam, newContested);
    }
}