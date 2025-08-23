package com.fullsteam.model.gamemodes;

public final class JuggernautInfo extends GameInfo {
    private final int team1Score;
    private final int team2Score;
    private final Long team1Juggernaut;
    private final Long team2Juggernaut;
    private final long timeLeft;

    public JuggernautInfo(int team1Score,
                          int team2Score,
                          Long team1Juggernaut,
                          Long team2Juggernaut,
                          long timeLeft) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.team1Juggernaut = team1Juggernaut;
        this.team2Juggernaut = team2Juggernaut;
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "Juggernaut";
    }

    public int getTeam1Score() {
        return team1Score;
    }

    public int getTeam2Score() {
        return team2Score;
    }

    public Long getTeam1Juggernaut() {
        return team1Juggernaut;
    }

    public Long getTeam2Juggernaut() {
        return team2Juggernaut;
    }

    public long getTimeLeft() {
        return timeLeft;
    }
}