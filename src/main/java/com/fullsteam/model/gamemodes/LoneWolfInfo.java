package com.fullsteam.model.gamemodes;

public final class LoneWolfInfo extends GameInfo {
    private final int loneWolfLives;

    public LoneWolfInfo(int loneWolfLives) {
        this.loneWolfLives = loneWolfLives;
    }

    @Override
    public String getType() {
        return "Lone Wolf";
    }

    public int getLoneWolfLives() {
        return loneWolfLives;
    }
}
