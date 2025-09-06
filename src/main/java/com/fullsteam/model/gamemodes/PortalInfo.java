package com.fullsteam.model.gamemodes;

import io.micronaut.core.annotation.Introspected;

@Introspected
public final class PortalInfo extends GameInfo {
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;

    public PortalInfo(double team1Score, double team2Score, long timeLeft) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "Portal";
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
