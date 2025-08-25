package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record Flag(int team,
                   FlagState state,
                   Vector2D position,
                   Vector2D basePosition,
                   Long carrierId,
                   long dropTimestamp // Used for automatic return timer
) {
    public enum FlagState {
        AT_BASE,
        CARRIED,
        DROPPED
    }

    // Helper methods for creating new instances with updated state (immutability)
    public Flag asCarriedBy(long playerId) {
        return new Flag(team, FlagState.CARRIED, position, basePosition, playerId, 0);
    }

    public Flag asDroppedAt(Vector2D dropPosition) {
        return new Flag(team, FlagState.DROPPED, dropPosition, basePosition, null, System.currentTimeMillis());
    }

    public Flag asReturned() {
        return new Flag(team, FlagState.AT_BASE, basePosition, basePosition, null, 0);
    }

    public Flag withPosition(Vector2D newPosition) {
        return new Flag(team, state, newPosition, basePosition, carrierId, dropTimestamp);
    }
}
