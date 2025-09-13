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
) implements Objective {
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
    
    // Objective interface implementations
    
    @Override
    public int getOwningTeam() {
        return owningTeam;
    }
    
    @Override
    public int getControllingTeam() {
        return controllingTeam;
    }
    
    @Override
    public boolean isContested() {
        return contested;
    }
    
    @Override
    public int getStrategicValue() {
        return 7; // Hills are high-value objectives
    }
    
    @Override
    public double getInteractionRadius() {
        return radius;
    }
    
    @Override
    public String getObjectiveType() {
        return owningTeam > 0 ? "Capture Point" : "Control Hill";
    }
    
    @Override
    public boolean canInteract(int team) {
        // In Blitz mode, teams can only capture opponent's hills
        if (owningTeam > 0) {
            return team != owningTeam;
        }
        // In KOTH mode, all teams can interact
        return true;
    }
    
    @Override
    public int getPriorityForTeam(int team) {
        int basePriority = getStrategicValue();
        
        // Blitz mode logic
        if (owningTeam > 0) {
            if (owningTeam == team) {
                // Defending our own hill - lower priority unless under attack
                basePriority = contested ? 6 : 3;
            } else {
                // Attacking opponent's hill - high priority
                basePriority = contested ? 9 : 8;
            }
        }
        // KOTH mode logic
        else {
            if (controllingTeam == team) {
                // We control it - maintain control
                basePriority = contested ? 7 : 4;
            } else {
                // We don't control it - capture it
                basePriority = contested ? 8 : 7;
            }
        }
        
        return basePriority;
    }
}