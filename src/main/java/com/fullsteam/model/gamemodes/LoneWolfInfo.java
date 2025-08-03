package com.fullsteam.model.gamemodes;

import java.util.List;

public final class LoneWolfInfo extends GameInfo {
    private final int loneWolfDeaths;
    private final List<String> huntersToKill;

    public LoneWolfInfo(int loneWolfDeaths,
                        List<String> huntersToKill) {
        this.loneWolfDeaths = loneWolfDeaths;
        this.huntersToKill = huntersToKill;
    }

    @Override
    public String getType() {
        return "Lone Wolf";
    }

    public int getLoneWolfDeaths() {
        return loneWolfDeaths;
    }

    public List<String> getHuntersToKill() {
        return huntersToKill;
    }
}
