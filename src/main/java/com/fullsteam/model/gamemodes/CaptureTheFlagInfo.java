package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Flag;

public final class CaptureTheFlagInfo extends GameInfo {
    private final Flag team1Flag;
    private final Flag team2Flag;
    private final double team1Score;
    private final double team2Score;
    private final long roundTimeRemainingSeconds;

    public CaptureTheFlagInfo(Flag team1Flag,
                              Flag team2Flag,
                              double team1Score,
                              double team2Score,
                              long roundTimeRemainingSeconds) {
        this.team1Flag = team1Flag;
        this.team2Flag = team2Flag;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.roundTimeRemainingSeconds = roundTimeRemainingSeconds;
    }

    @Override
    public String getType() {
        return "Capture the Flag";
    }

    public Flag getTeam1Flag() {
        return team1Flag;
    }

    public Flag getTeam2Flag() {
        return team2Flag;
    }

    public double getTeam1Score() {
        return team1Score;
    }

    public double getTeam2Score() {
        return team2Score;
    }

    public long getRoundTimeRemainingSeconds() {
        return roundTimeRemainingSeconds;
    }
}