package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Crate;

import java.util.List;

public final class BuilderGameInfo extends GameInfo {
    private final List<Crate> crates;

    public BuilderGameInfo(List<Crate> crates) {
        this.crates = crates;
    }

    @Override
    public String getType() {
        return "Builder";
    }

    public List<Crate> getCrates() {
        return crates;
    }
}
