package com.fullsteam.model.gamemodes;

public class FreeForAllInfo extends GameInfo {

    private final long roundTimeRemainingSeconds;

    public FreeForAllInfo(long roundTimeRemainingSeconds) {
        this.roundTimeRemainingSeconds = roundTimeRemainingSeconds;
    }

    @Override
    public String getType() {
        return "Free For All";
    }

    public long getRoundTimeRemainingSeconds() {
        return roundTimeRemainingSeconds;
    }

}