package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.TeamDeathmatchInfo;
import io.micronaut.context.annotation.Prototype;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Prototype
public class TeamDeathmatchManager extends AbstractTeamBasedManager {

    public TeamDeathmatchManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        applyDeathmatchScoring();
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