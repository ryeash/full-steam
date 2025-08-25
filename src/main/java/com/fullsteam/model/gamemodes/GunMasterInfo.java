package com.fullsteam.model.gamemodes;

public final class GunMasterInfo extends FreeForAllInfo {

    public GunMasterInfo(long timeLeft) {
        super(timeLeft);
    }

    @Override
    public String getType() {
        return "Gun Master";
    }
}