package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Oddball;

public final class OddballInfo extends GameInfo {
    private final Oddball oddball;
    private final double team1Score;
    private final double team2Score;
    private final long roundTimeRemainingSeconds;

    public OddballInfo(Oddball oddball,
                       double team1Score,
                       double team2Score,
                       long roundTimeRemainingSeconds) {
        this.oddball = oddball;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.roundTimeRemainingSeconds = roundTimeRemainingSeconds;
    }

    @Override
    public String getType() {
        return "Oddball";
    }

    public Oddball getOddball() {
        return oddball;
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