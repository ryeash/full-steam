package com.fullsteam.model.gamemodes;

import java.util.List;

public class FreeForAllInfo extends GameInfo {

    private final List<PlayerScore> scores;
    private final long roundTimeRemainingSeconds;

    public FreeForAllInfo(List<PlayerScore> scores, long roundTimeRemainingSeconds) {
        this.scores = scores;
        this.roundTimeRemainingSeconds = roundTimeRemainingSeconds;
    }

    @Override
    public String getType() {
        return "Free For All";
    }

    public List<PlayerScore> getScores() {
        return scores;
    }

    public long getRoundTimeRemainingSeconds() {
        return roundTimeRemainingSeconds;
    }

    public static class PlayerScore {
        private final String playerName;
        private final int score;

        public PlayerScore(String playerName, int score) {
            this.playerName = playerName;
            this.score = score;
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getScore() {
            return score;
        }
    }
}