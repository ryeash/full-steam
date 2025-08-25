package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.TeamDeathmatchInfo;

import java.util.concurrent.TimeUnit;

public class TeamDeathmatchManager extends AbstractTeamBasedManager {

    public TeamDeathmatchManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (shooter != null) {
            if (shooter.getTeam() == 1) {
                team1Score++;
            } else {
                team2Score++;
            }
        }
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new TeamDeathmatchInfo(
                team1Score,
                team2Score,
                timeLeft);
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
}