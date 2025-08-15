package com.fullsteam.model.gamemodes;

public final class GunMasterInfo extends FreeForAllInfo {

    public GunMasterInfo(long roundTimeRemainingSeconds) {
        super(roundTimeRemainingSeconds);
    }

    @Override
    public String getType() {
        return "Gun Master";
    }
}