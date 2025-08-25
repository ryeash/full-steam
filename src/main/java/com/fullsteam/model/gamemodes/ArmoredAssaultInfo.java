package com.fullsteam.model.gamemodes;

import com.fullsteam.model.MotorPool;

import java.util.List;

/**
 * A record containing the specific state information for an Armored Assault game.
 * When serialized, Jackson will automatically add a "gameType": "Armored Assault"
 * property because of the annotations in the GameInfo base class.
 */
public final class ArmoredAssaultInfo extends GameInfo {
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;
    private final List<MotorPool> motorPools;

    public ArmoredAssaultInfo(double team1Score,
                              double team2Score,
                              long timeLeft,
                              List<MotorPool> motorPools) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
        this.motorPools = motorPools;
    }

    @Override
    public String getType() {
        return "Armored Assault";
    }

    public double getTeam1Score() {
        return team1Score;
    }

    public double getTeam2Score() {
        return team2Score;
    }

    public long getTimeLeft() {
        return timeLeft;
    }

    public List<MotorPool> getMotorPools() {
        return motorPools;
    }
}