package com.fullsteam.model.gamemodes;

public class FreeForAllInfo extends GameInfo {

    private final long timeLeft;

    public FreeForAllInfo(long timeLeft) {
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "Free For All";
    }

    public long gettimeLeft() {
        return timeLeft;
    }

}