package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Oddball;

public final class OddballInfo extends GameInfo {
    private final Oddball oddball;
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;

    public OddballInfo(Oddball oddball,
                       double team1Score,
                       double team2Score,
                       long timeLeft) {
        this.oddball = oddball;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
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

    public long getTimeLeft() {
        return timeLeft;
    }
}