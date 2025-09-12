package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record Flag(int team,
                   FlagState state,
                   Vector2D position,
                   Vector2D basePosition,
                   Long carrierId,
                   long dropTimestamp // Used for automatic return timer
) implements Objective {
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
    
    // Objective interface implementations
    
    @Override
    public int getOwningTeam() {
        return team; // The flag belongs to this team
    }
    
    @Override
    public int getControllingTeam() {
        // If carried, the carrier's team controls it; otherwise it's uncontrolled
        return state == FlagState.CARRIED && carrierId != null ? 
            (team == 1 ? 2 : 1) : // Opposite team has it
            0; // No one controls it
    }
    
    @Override
    public boolean isContested() {
        // Flags aren't really "contested" in the traditional sense
        return false;
    }
    
    @Override
    public int getStrategicValue() {
        return switch (state) {
            case AT_BASE -> 6; // Enemy flag at base - medium-high value to capture
            case DROPPED -> 8; // Dropped flag - high value to pick up or return
            case CARRIED -> team == getCarrierTeam() ? 3 : 9; // If enemy has our flag, very high priority to recover
        };
    }
    
    @Override
    public double getInteractionRadius() {
        return 30.0; // Flags have smaller interaction radius than hills
    }
    
    @Override
    public String getObjectiveType() {
        return switch (state) {
            case AT_BASE -> "Enemy Flag";
            case DROPPED -> "Dropped Flag";
            case CARRIED -> "Recover Flag";
        };
    }
    
    @Override
    public boolean canInteract(int teamId) {
        return switch (state) {
            case AT_BASE -> teamId != team; // Only enemy team can capture flag at base
            case DROPPED -> true; // Anyone can pick up dropped flag
            case CARRIED -> teamId == team; // Only flag's team can recover their carried flag
        };
    }
    
    @Override
    public int getPriorityForTeam(int teamId) {
        return switch (state) {
            case AT_BASE -> {
                if (teamId != team) {
                    yield 7; // High priority for enemy to capture our flag
                } else {
                    yield 2; // Low priority for us to defend flag at base
                }
            }
            case DROPPED -> {
                if (teamId == team) {
                    yield 8; // High priority to return our own flag
                } else {
                    yield 6; // Medium-high priority for enemy to pick up our flag
                }
            }
            case CARRIED -> {
                if (teamId == team) {
                    yield 9; // Very high priority to recover our flag
                } else {
                    yield 3; // Low priority for enemy (they already have it)
                }
            }
        };
    }
    
    /**
     * Helper method to get the team of the flag carrier.
     */
    private int getCarrierTeam() {
        // This would need to be determined by looking up the carrier in the game state
        // For now, assume it's the opposite team
        return team == 1 ? 2 : 1;
    }
}
