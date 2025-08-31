package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.GameLobby;
import com.fullsteam.model.gamemodes.FreeForAllInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.context.annotation.Prototype;

@Prototype
public class FreeForAllManager extends AbstractFreeForAllManager {

    public FreeForAllManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    protected GameInfo buildGameInfo() {
        return new FreeForAllInfo(Math.max(0, (roundEndTime - System.currentTimeMillis()) / 1000));
    }
}
