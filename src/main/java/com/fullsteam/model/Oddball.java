package com.fullsteam.model;

/**
 * Represents the state of the Oddball object in the game.
 * This is an immutable record, so state changes create a new instance.
 */
public record Oddball(OddballState state,
                      Vector2D position,
                      Long carrierId,
                      long dropTimestamp // Used for automatic reset timer
) {
    public enum OddballState {
        ON_SPAWN, // At its central spawn point
        CARRIED,  // Held by a player
        DROPPED   // On the ground after a carrier was eliminated
    }

    // --- Helper methods for immutable state transitions ---

    public Oddball asCarriedBy(Long playerId, Vector2D carrierPosition) {
        return new Oddball(OddballState.CARRIED, carrierPosition, playerId, 0);
    }

    public Oddball asDroppedAt(Vector2D dropPosition) {
        return new Oddball(OddballState.DROPPED, dropPosition, null, System.currentTimeMillis());
    }

    public Oddball asReset(Vector2D spawnPosition) {
        return new Oddball(OddballState.ON_SPAWN, spawnPosition, null, 0);
    }

    public Oddball withPosition(Vector2D newPosition) {
        return new Oddball(state, newPosition, carrierId, dropTimestamp);
    }
}