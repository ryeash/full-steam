package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Payload;

public final class EscortGameInfo extends GameInfo {
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;
    private final Payload payload;

    public EscortGameInfo(double team1Score, double team2Score, long timeLeft, Payload payload) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
        this.payload = payload;
    }

    @Override
    public String getType() {
        return "Escort";
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

    public Payload getPayload() {
        return payload;
    }

    public double getCaptureRadius() {
        return payload.getCaptureRadius();
    }
}
