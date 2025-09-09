package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Base;
import com.fullsteam.model.MotorPool;

import java.util.List;

/**
 * Contains the game-state information specific to the Dual Base Destruction game mode.
 * Both teams have a base to defend and must destroy the enemy base while protecting their own.
 * Motor pools allow teams to spawn vehicles to aid in attack and defense.
 */
public final class BaseDestructionInfo extends GameInfo {
    private final Base team1Base;
    private final Base team2Base;
    private final long timeLeft;
    private final boolean team1BaseDestroyed;
    private final boolean team2BaseDestroyed;
    private final List<MotorPool> motorPools;
    private final double team1Score;
    private final double team2Score;

    /**
     * @param team1Base          Team 1's base
     * @param team2Base          Team 2's base
     * @param timeLeft           The time left in the current round
     * @param team1BaseDestroyed Whether Team 1's base has been destroyed
     * @param team2BaseDestroyed Whether Team 2's base has been destroyed
     * @param motorPools         List of motor pools for vehicle spawning
     * @param team1Score         Team 1's current score
     * @param team2Score         Team 2's current score
     */
    public BaseDestructionInfo(Base team1Base, Base team2Base, long timeLeft,
                               boolean team1BaseDestroyed, boolean team2BaseDestroyed,
                               List<MotorPool> motorPools, double team1Score, double team2Score) {
        this.team1Base = team1Base;
        this.team2Base = team2Base;
        this.timeLeft = timeLeft;
        this.team1BaseDestroyed = team1BaseDestroyed;
        this.team2BaseDestroyed = team2BaseDestroyed;
        this.motorPools = motorPools;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
    }

    @Override
    public String getType() {
        return "Base Destruction";
    }

    public Base getTeam1Base() {
        return team1Base;
    }

    public Base getTeam2Base() {
        return team2Base;
    }

    public long getTimeLeft() {
        return timeLeft;
    }

    public boolean isTeam1BaseDestroyed() {
        return team1BaseDestroyed;
    }

    public boolean isTeam2BaseDestroyed() {
        return team2BaseDestroyed;
    }

    public List<MotorPool> getMotorPools() {
        return motorPools;
    }

    public double getTeam1Score() {
        return team1Score;
    }

    public double getTeam2Score() {
        return team2Score;
    }
}
