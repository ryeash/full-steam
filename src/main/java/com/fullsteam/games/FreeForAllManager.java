package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.FreeForAllInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@GameName("Free for All")
public class FreeForAllManager extends AbstractFreeForAllManager {

    public FreeForAllManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected GameInfo buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        List<FreeForAllInfo.PlayerScore> playerScores = players.values().stream()
                .sorted(Comparator.comparingInt(Player::getKills).reversed()) // Sort by kills descending
                .map(player -> new FreeForAllInfo.PlayerScore(player.getPlayerName(), player.getKills()))
                .collect(Collectors.toList());

        return new FreeForAllInfo(
                playerScores,
                roundTimeRemainingSeconds);
    }
}
