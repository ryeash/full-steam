package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.TeamBalancer;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.TeamDeathmatchInfo;

import java.util.concurrent.TimeUnit;

public class TeamDeathmatchManager extends AbstractTeamBasedManager {
    protected final TeamBalancer teamBalancer;

    public TeamDeathmatchManager(GameLobby gameLobby) {
        super(gameLobby);
        this.teamBalancer = new TeamBalancer(this);
    }

    @Override
    public String gameType() {
        return "Team Deathmatch";
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (shooter.getTeam() == 1) {
            team1Score++;
        } else {
            team2Score++;
        }
    }

    @Override
    protected GameState buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new GameState(
                players.values(),
                bullets,
                obstacles,
                deathMarkers,
                gameEvents,
                new TeamDeathmatchInfo(
                        team1Score,
                        team2Score,
                        roundTimeRemainingSeconds));
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() >= roundEndTime) {
            sendVictoryMessage();
            log.info("Round timer has expired. Starting a new round.");
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void updateGame() {
        teamBalancer.balanceTeams(players);
        super.updateGame();
    }
}