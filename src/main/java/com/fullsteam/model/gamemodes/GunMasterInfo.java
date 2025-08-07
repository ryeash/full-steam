package com.fullsteam.model.gamemodes;

import java.util.List;

public class GunMasterInfo extends FreeForAllInfo {

    public GunMasterInfo(List<FreeForAllInfo.PlayerScore> scores, long roundTimeRemainingSeconds) {
        super(scores, roundTimeRemainingSeconds);
    }

    @Override
    public String getType() {
        return "Gun Master";
    }
}