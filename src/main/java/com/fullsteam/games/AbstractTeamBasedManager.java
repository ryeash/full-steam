package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.TeamBalancer;
import com.fullsteam.model.GameEvent;

import static com.fullsteam.Config.ROUND_DURATION_SECONDS;

/**
 * Manages the game logic for the King of the Hill mode.
 * The objective is to control a central point to accumulate score.
 */
public abstract class AbstractTeamBasedManager extends AbstractGameStateManager {

    protected double team1Score;
    protected double team2Score;
    protected final TeamBalancer teamBalancer;
    protected boolean sentVictoryMessage = false;
    protected long roundEndTime = 0;

    public AbstractTeamBasedManager(GameLobby gameLobby) {
        super(gameLobby);
        this.teamBalancer = new TeamBalancer(this);
    }

    protected void sendVictoryMessage() {
        if (sentVictoryMessage) {
            return;
        }
        sentVictoryMessage = true;
        int winningTeam = winningTeam();
        if (winningTeam > 0) {
            sendGameEvent(GameEvent.team(winningTeam, "Team %d wins with a score of %d-%d!".formatted(winningTeam, (int) team1Score, (int) team2Score)));
        } else {
            sendGameEvent(GameEvent.info("The game ended in a draw"));
        }
        resetScore();
    }

    protected int winningTeam() {
        if (team1Score > team2Score) {
            return 1;
        } else if (team2Score > team1Score) {
            return 2;
        } else {
            return -1;
        }
    }

    protected void resetScore() {
        team1Score = 0;
        team2Score = 0;
    }

    @Override
    protected void startNewRound() {
        resetScore();
        this.roundEndTime = System.currentTimeMillis() + (ROUND_DURATION_SECONDS * 1000);
        this.sentVictoryMessage = false;
        super.startNewRound();
    }

    @Override
    protected void updateGame() {
        teamBalancer.balanceTeams(players);
        super.updateGame();
    }
}