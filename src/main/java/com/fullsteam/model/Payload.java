package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Represents an escort payload that teams must push to their objective zones.
 * The payload is an Obstacle that can be moved by player proximity and implements
 * the Objective interface for AI decision-making.
 */
@Introspected
public class Payload extends Obstacle implements Objective {

    private final double captureRadius;
    @JsonIgnore
    private final double leftBoundary;
    @JsonIgnore
    private final double rightBoundary;
    
    public Payload(List<Vector2D> vertices, double captureRadius, double leftBoundary, double rightBoundary) {
        super(vertices, false); // Payload is not destructible
        this.captureRadius = captureRadius;
        this.leftBoundary = leftBoundary;
        this.rightBoundary = rightBoundary;
    }
    
    /**
     * Creates a new Payload with updated position while preserving other properties.
     */
    public Payload withVertices(List<Vector2D> newVertices) {
        return new Payload(newVertices, captureRadius, leftBoundary, rightBoundary);
    }
    
    /**
     * Gets the capture radius for this payload.
     */
    public double getCaptureRadius() {
        return captureRadius;
    }
    
    /**
     * Gets the left boundary position (Team 2's goal).
     */
    public double getLeftBoundary() {
        return leftBoundary;
    }
    
    /**
     * Gets the right boundary position (Team 1's goal).
     */
    public double getRightBoundary() {
        return rightBoundary;
    }
    
    /**
     * Calculates the progress percentage towards Team 1's goal (0.0 to 1.0).
     * 0.0 = at left boundary (Team 2's goal), 1.0 = at right boundary (Team 1's goal)
     */
    @JsonIgnore
    public double getProgressToTeam1Goal() {
        double currentX = getCenter().x();
        double totalDistance = rightBoundary - leftBoundary;
        double progressDistance = currentX - leftBoundary;
        return Math.max(0.0, Math.min(1.0, progressDistance / totalDistance));
    }

    /**
     * Calculates the progress percentage towards Team 2's goal (0.0 to 1.0).
     * 0.0 = at right boundary (Team 1's goal), 1.0 = at left boundary (Team 2's goal)
     */
    @JsonIgnore
    public double getProgressToTeam2Goal() {
        return 1.0 - getProgressToTeam1Goal();
    }
    
    /**
     * Determines which team is closer to victory based on payload position.
     */
    @JsonIgnore
    public int getLeadingTeam() {
        double progress = getProgressToTeam1Goal();
        if (progress > 0.5) {
            return 1; // Team 1 is closer to their goal
        } else if (progress < 0.5) {
            return 2; // Team 2 is closer to their goal
        } else {
            return 0; // Exactly in the middle
        }
    }
    
    // Objective interface implementations
    
    @Override
    public Vector2D position() {
        return getCenter();
    }

    @Override
    public int getOwningTeam() {
        return 0; // Payload is neutral - no team owns it
    }
    
    @Override
    public int getControllingTeam() {
        // The payload doesn't have a persistent controlling team
        // Control is determined dynamically by proximity in the game manager
        return 0; // No persistent control
    }
    
    @Override
    public boolean isContested() {
        // Payload could be considered contested if multiple teams are nearby
        // For now, return false as we don't have proximity data here
        return false;
    }
    
    @Override
    public int getStrategicValue() {
        return 10; // Maximum value - payload is the primary objective
    }
    
    @Override
    public double getInteractionRadius() {
        return captureRadius;
    }
    
    @Override
    public String getObjectiveType() {
        return "Payload";
    }
    
    @Override
    public boolean canInteract(int team) {
        return true; // All teams can interact with the payload
    }
    
    @Override
    public int getPriorityForTeam(int team) {
        double progress = getProgressToTeam1Goal();
        
        if (team == 1) {
            // Team 1 wants to push payload right (towards progress = 1.0)
            if (progress >= 0.8) {
                return 10; // Very high priority - close to victory
            } else if (progress >= 0.6) {
                return 9; // High priority - good progress
            } else if (progress <= 0.2) {
                return 8; // High priority - need to recover
            } else {
                return 7; // Standard high priority
            }
        } else if (team == 2) {
            // Team 2 wants to push payload left (towards progress = 0.0)
            if (progress <= 0.2) {
                return 10; // Very high priority - close to victory
            } else if (progress <= 0.4) {
                return 9; // High priority - good progress
            } else if (progress >= 0.8) {
                return 8; // High priority - need to recover
            } else {
                return 7; // Standard high priority
            }
        }
        
        return 7; // Default high priority for unknown teams
    }
    
    /**
     * Enhanced priority calculation that considers current team control.
     * This method can be used by the AI system when it has proximity information.
     * 
     * @param team The team requesting priority
     * @param controllingTeam The team currently controlling the payload (0 if contested/neutral)
     * @param isContested Whether multiple teams are near the payload
     * @return Priority value adjusted for control context
     */
    public int getPriorityForTeamWithControl(int team, int controllingTeam, boolean isContested) {
        int basePriority = getPriorityForTeam(team);
        
        if (isContested) {
            // Contested payload is always high priority
            return Math.min(10, basePriority + 2);
        } else if (controllingTeam == team) {
            // We're controlling it - maintain presence but slightly lower priority
            return Math.max(1, basePriority - 1);
        } else if (controllingTeam > 0 && controllingTeam != team) {
            // Enemy controls it - very high priority to contest
            return Math.min(10, basePriority + 2);
        } else {
            // No one controlling - standard priority
            return basePriority;
        }
    }
}
