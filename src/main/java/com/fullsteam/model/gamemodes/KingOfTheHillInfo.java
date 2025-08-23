package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Hill;

/**
 * Contains the specific state for a King of the Hill game.
 * This object is embedded within the main GameState.
 */
public final class KingOfTheHillInfo extends GameInfo {
    private final Hill hill;
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;

    public KingOfTheHillInfo(Hill hill,
                             double team1Score, // Scores are doubles to allow for fractional points per tick
                             double team2Score,
                             long timeLeft) {
        this.hill = hill;
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "King of the Hill";
    }

    public Hill getHill() {
        return hill;
    }

    public double getTeam1Score() {
        return team1Score;
    }

    public double getTeam2Score() {
        return team2Score;
    }

    public long gettimeLeft() {
        return timeLeft;
    }
}