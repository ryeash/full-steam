package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

/**
 * Represents the state of the Oddball object in the game.
 * This is an immutable record, so state changes create a new instance.
 */
@Introspected
public record Oddball(OddballState state,
                      Vector2D position,
                      Long carrierId,
                      long dropTimestamp // Used for automatic reset timer
) implements Objective {
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
    
    // Objective interface implementations
    
    @Override
    public int getOwningTeam() {
        return 0; // Oddball is neutral - no team owns it
    }
    
    @Override
    public int getControllingTeam() {
        // If carried, the carrier's team controls it; otherwise it's uncontrolled
        // Since we don't have access to game state here, we return 0 (uncontrolled)
        // The AI system will use the enhanced priority calculation with context
        return 0; // No one controls it (context-dependent)
    }
    
    @Override
    public boolean isContested() {
        // Oddball could be considered contested if multiple players are nearby
        // For now, return false as we don't have proximity data here
        return false;
    }
    
    @Override
    public int getStrategicValue() {
        return switch (state) {
            case ON_SPAWN -> 8; // High value - fresh oddball to pick up
            case DROPPED -> 9; // Very high value - dropped oddball, easy pickup
            case CARRIED -> 7; // High value - need to eliminate carrier
        };
    }
    
    @Override
    public double getInteractionRadius() {
        return 25.0; // Smaller radius than hills, similar to flags
    }
    
    @Override
    public String getObjectiveType() {
        return switch (state) {
            case ON_SPAWN -> "Oddball (Available)";
            case DROPPED -> "Oddball (Dropped)";
            case CARRIED -> "Oddball (Carried)";
        };
    }
    
    @Override
    public boolean canInteract(int team) {
        // Anyone can pick up available oddball
        // Anyone can try to eliminate the carrier
        return true;
    }
    
    @Override
    public int getPriorityForTeam(int team) {
        return switch (state) {
            case ON_SPAWN -> 8; // High priority for all teams to grab it first
            case DROPPED -> 9; // Very high priority - easy pickup opportunity
            case CARRIED -> {
                // When carried, we can't determine the carrier's team without game state context
                // So we provide a reasonable default: high priority for all teams
                // - If it's our carrier, we want to protect them
                // - If it's enemy carrier, we want to eliminate them
                yield 7; // High priority for carried oddball (context-dependent)
            }
        };
    }
    
    /**
     * Enhanced priority calculation that takes carrier team into account.
     * This method can be used by the AI system when it has access to game state.
     * 
     * @param team The team requesting priority
     * @param carrierTeam The team of the current carrier (0 if unknown)
     * @return Priority value adjusted for carrier team context
     */
    public int getPriorityForTeamWithCarrier(int team, int carrierTeam) {
        return switch (state) {
            case ON_SPAWN -> 8; // High priority for all teams to grab it first
            case DROPPED -> 9; // Very high priority - easy pickup opportunity
            case CARRIED -> {
                if (carrierTeam == team) {
                    yield 4; // Medium priority to protect our carrier
                } else if (carrierTeam > 0) {
                    yield 9; // Very high priority to eliminate enemy carrier
                } else {
                    yield 7; // Unknown carrier team - high priority
                }
            }
        };
    }
}