package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.games.AbstractGameStateManager;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class ActiveGame {
    private final String gameType;
    @JsonIgnore
    private final AbstractGameStateManager game;

    public ActiveGame(String gameType, AbstractGameStateManager game) {
        this.gameType = gameType;
        this.game = game;
    }

    public long getGameId() {
        return game.getGameId();
    }

    public String getGameType() {
        return gameType;
    }

    public int getPlayerCount() {
        return game.getPlayerCount();
    }

    public int getMaxPlayers() {
        return game.getMaxPlayers();
    }

    public long getCreatedTime() {
        return game.getCreatedTime();
    }

    public AbstractGameStateManager getGame() {
        return game;
    }

}
