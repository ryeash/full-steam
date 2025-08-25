package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

/**
 * Represents the state of a Motor Pool zone in Armored Assault.
 *
 * @param position               The center coordinates of the motor pool.
 * @param radius                 The radius of the capture zone.
 * @param controllingTeam        The team currently in control (0=neutral, 1=team1, 2=team2).
 * @param contested              True if players from both teams are in the zone.
 * @param controlStartTime       When the current team started controlling (0 if neutral/contested).
 * @param timeToControl          Time in milliseconds needed to fully control the motor pool.
 */
@Introspected
public record MotorPool(
        Vector2D position,
        double radius,
        @JsonIgnore double radiusSq,
        int controllingTeam,
        boolean contested,
        long controlStartTime,
        long timeToControl
) {
    /**
     * Returns a new MotorPool instance with an updated state.
     * This is used to maintain immutability.
     */
    public MotorPool withState(int newControllingTeam, boolean newContested, long newControlStartTime) {
        return new MotorPool(this.position, this.radius, this.radiusSq, newControllingTeam, newContested, newControlStartTime, this.timeToControl);
    }

    /**
     * Checks if the motor pool is fully controlled by a team.
     */
    public boolean isFullyControlled() {
        return !contested && controllingTeam > 0 && 
               (System.currentTimeMillis() - controlStartTime) >= timeToControl;
    }

    /**
     * Gets the control progress as a percentage (0.0 to 1.0).
     */
    public double getControlProgress() {
        if (contested || controllingTeam <= 0) {
            return 0.0;
        }
        long elapsed = System.currentTimeMillis() - controlStartTime;
        return Math.min(1.0, (double) elapsed / timeToControl);
    }
}