package com.fullsteam.model.gamemodes;

/**
 * A record containing the specific state information for a Team Deathmatch game.
 * When serialized, Jackson will automatically add a "gameType": "Team Deathmatch"
 * property because of the annotations in the GameInfo base class.
 */
public final class TeamDeathmatchInfo extends GameInfo {
    private final double team1Score;
    private final double team2Score;
    private final long timeLeft;

    public TeamDeathmatchInfo(double team1Score,
                              double team2Score,
                              long timeLeft) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "Team Deathmatch";
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