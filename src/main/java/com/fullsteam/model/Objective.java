package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Marker interface for game objectives that AI can identify and pursue.
 * Implementing this interface indicates that an object represents a strategic
 * objective that should be considered in AI decision-making.
 * <p>
 * Examples include:
 * - Capture points (Hills)
 * - Flags in Capture the Flag
 * - Bases to attack or defend
 * - Control points
 * - Payloads to escort
 * <p>
 * This interface enables the UnifiedAIStrategy to automatically discover
 * and prioritize objectives without game mode-specific code.
 */
public interface Objective {

    /**
     * Gets the position of this objective.
     *
     * @return The world coordinates of the objective
     */
    Vector2D position();

    /**
     * Gets the team that owns this objective, if any.
     *
     * @return Team ID (1, 2, etc.) or 0 for neutral objectives
     */
    @JsonIgnore
    default int getOwningTeam() {
        return 0; // Default to neutral
    }

    /**
     * Gets the team currently controlling this objective, if any.
     *
     * @return Team ID (1, 2, etc.) or 0 for uncontrolled objectives
     */
    default int getControllingTeam() {
        return 0; // Default to uncontrolled
    }

    /**
     * Determines if this objective is contested (multiple teams present).
     *
     * @return true if contested, false otherwise
     */
    @JsonIgnore
    default boolean isContested() {
        return false; // Default to not contested
    }

    /**
     * Gets the strategic value of this objective for AI prioritization.
     * Higher values indicate more important objectives.
     *
     * @return Strategic value (1-10 scale recommended)
     */
    @JsonIgnore
    default int getStrategicValue() {
        return 5; // Default medium value
    }

    /**
     * Gets the interaction radius for this objective.
     *
     * @return Radius in world units for interaction/capture
     */
    @JsonIgnore
    default double getInteractionRadius() {
        return 50.0; // Default interaction radius
    }

    /**
     * Gets a human-readable description of this objective type.
     *
     * @return Description for debugging and UI purposes
     */
    @JsonIgnore
    default String getObjectiveType() {
        return "Generic Objective";
    }

    /**
     * Determines if this objective can be interacted with by the given team.
     *
     * @param team The team ID to check
     * @return true if the team can interact with this objective
     */
    @JsonIgnore
    default boolean canInteract(int team) {
        return true; // Default to allowing all teams
    }

    /**
     * Determines the priority this objective should have for the given team.
     * This allows objectives to provide context-aware priority calculations.
     *
     * @param team The team ID requesting priority
     * @return Priority value (higher = more important)
     */
    @JsonIgnore
    default int getPriorityForTeam(int team) {
        // Default implementation based on ownership and control
        int basePriority = getStrategicValue();

        // Higher priority if we don't control it but can capture it
        if (getControllingTeam() != team && canInteract(team)) {
            basePriority += 3;
        }

        // Lower priority if we already control it (defensive)
        if (getControllingTeam() == team) {
            basePriority = Math.max(1, basePriority - 2);
        }

        // Higher priority if contested
        if (isContested()) {
            basePriority += 2;
        }

        return basePriority;
    }
}
