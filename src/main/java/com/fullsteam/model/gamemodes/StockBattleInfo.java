package com.fullsteam.model.gamemodes;

import io.micronaut.core.annotation.Introspected;

/**
 * Contains the game-state information specific to the Stock Battle game mode.
 * Teams have limited revives (stock) and must eliminate the enemy team completely.
 */
@Introspected
public final class StockBattleInfo extends GameInfo {
    private final int team1Stock;
    private final int team2Stock;
    private final int team1AlivePlayers;
    private final int team2AlivePlayers;
    private final long timeLeft;

    /**
     * @param team1Stock        Team 1's remaining revives/stock
     * @param team2Stock        Team 2's remaining revives/stock
     * @param team1AlivePlayers Number of alive players on Team 1
     * @param team2AlivePlayers Number of alive players on Team 2
     * @param timeLeft          Time remaining in the round (seconds)
     */
    public StockBattleInfo(int team1Stock, int team2Stock,
                           int team1AlivePlayers, int team2AlivePlayers, long timeLeft) {
        this.team1Stock = team1Stock;
        this.team2Stock = team2Stock;
        this.team1AlivePlayers = team1AlivePlayers;
        this.team2AlivePlayers = team2AlivePlayers;
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "Stock Battle";
    }

    public int getTeam1Stock() {
        return team1Stock;
    }

    public int getTeam2Stock() {
        return team2Stock;
    }

    public int getTeam1AlivePlayers() {
        return team1AlivePlayers;
    }

    public int getTeam2AlivePlayers() {
        return team2AlivePlayers;
    }

    public long getTimeLeft() {
        return timeLeft;
    }
}
