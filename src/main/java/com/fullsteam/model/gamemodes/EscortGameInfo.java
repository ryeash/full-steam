package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Obstacle;

public final class EscortGameInfo extends GameInfo {
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;
    private final Obstacle obstacle;
    private final double captureRadius;

    public EscortGameInfo(double team1Score, double team2Score, long timeLeft, Obstacle obstacle, double captureRadius) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
        this.obstacle = obstacle;
        this.captureRadius = captureRadius;
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

    public Obstacle getObstacle() {
        return obstacle;
    }

    public double getCaptureRadius() {
        return captureRadius;
    }
}
