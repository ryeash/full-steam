package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents the state of the capture point ("the hill") in King of the Hill and Blitz modes.
 *
 * @param position        The center coordinates of the hill.
 * @param radius          The radius of the capture zone.
 * @param controllingTeam The team currently in control (0=neutral, 1=team1, 2=team2).
 * @param contested       True if players from both teams are on the hill.
 * @param owningTeam      The team that owns this hill (0=neutral, 1=team1, 2=team2). Used in Blitz mode.
 */
@Introspected
public record Hill(
        Vector2D position,
        double radius,
        @JsonIgnore double radiusSq,
        int controllingTeam,
        boolean contested,
        int owningTeam
) {
    /**
     * Returns a new Hill instance with an updated state.
     * This is used to maintain immutability.
     */
    public Hill withState(int newControllingTeam, boolean newContested) {
        return new Hill(this.position, this.radius, this.radiusSq, newControllingTeam, newContested, this.owningTeam);
    }
    
    /**
     * Backward compatibility constructor for King of the Hill mode (no owning team).
     */
    public Hill(Vector2D position, double radius, double radiusSq, int controllingTeam, boolean contested) {
        this(position, radius, radiusSq, controllingTeam, contested, 0); // 0 = neutral/no owner
    }
}