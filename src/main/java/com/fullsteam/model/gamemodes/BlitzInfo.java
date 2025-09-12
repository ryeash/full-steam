package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Hill;

import java.util.List;

/**
 * Contains the game-state information specific to the Blitz game mode.
 * Teams must capture opponent's capture points while defending their own.
 */
public final class BlitzInfo extends GameInfo {
    private final List<Hill> team1CapturePoints;
    private final List<Hill> team2CapturePoints;
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;

    public BlitzInfo(List<Hill> team1CapturePoints, List<Hill> team2CapturePoints, 
                     double team1Score, double team2Score, long timeLeft) {
        this.team1CapturePoints = team1CapturePoints;
        this.team2CapturePoints = team2CapturePoints;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
    }

    public List<Hill> getTeam1CapturePoints() {
        return team1CapturePoints;
    }

    public List<Hill> getTeam2CapturePoints() {
        return team2CapturePoints;
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

    @Override
    public String getType() {
        return "Blitz";
    }
}
