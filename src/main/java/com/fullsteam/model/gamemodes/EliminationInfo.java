package com.fullsteam.model.gamemodes;

/**
 * Contains the game-state information specific to the Elimination game mode.
 */
public final class EliminationInfo extends GameInfo {
    private final double team1Score;
    private final double team2Score;
    private final long team1PlayersAlive;
    private final long team2PlayersAlive;
    private final long roundTimeRemainingSeconds;

    /**
     * @param team1Score                The current score for Team 1.
     * @param team2Score                The current score for Team 2.
     * @param team1PlayersAlive         The number of players still alive on Team 1.
     * @param team2PlayersAlive         The number of players still alive on Team 2.
     * @param roundTimeRemainingSeconds The time left in the current round.
     */
    public EliminationInfo(
            double team1Score,
            double team2Score,
            long team1PlayersAlive,
            long team2PlayersAlive,
            long roundTimeRemainingSeconds
    ) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.team1PlayersAlive = team1PlayersAlive;
        this.team2PlayersAlive = team2PlayersAlive;
        this.roundTimeRemainingSeconds = roundTimeRemainingSeconds;
    }

    @Override
    public String getType() {
        return "Elimination";
    }

    public double getTeam1Score() {
        return team1Score;
    }

    public double getTeam2Score() {
        return team2Score;
    }

    public long getTeam1PlayersAlive() {
        return team1PlayersAlive;
    }

    public long getTeam2PlayersAlive() {
        return team2PlayersAlive;
    }

    public long getRoundTimeRemainingSeconds() {
        return roundTimeRemainingSeconds;
    }
}