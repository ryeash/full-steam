package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.model.gamemodes.FreeForAllInfo;
import com.fullsteam.model.gamemodes.GameInfo;

public class FreeForAllManager extends AbstractFreeForAllManager {

    public FreeForAllManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected GameInfo buildGameInfo() {
        return new FreeForAllInfo(Math.max(0, (roundEndTime - System.currentTimeMillis()) / 1000));
    }
}
