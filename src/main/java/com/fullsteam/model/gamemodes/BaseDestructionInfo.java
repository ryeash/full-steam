package com.fullsteam.model.gamemodes;

import com.fullsteam.model.Base;

/**
 * Contains the game-state information specific to the Base Destruction game mode.
 * Team 1 (attackers) tries to destroy Team 2's (defenders) base before time runs out.
 */
public final class BaseDestructionInfo extends GameInfo {
    private final Base base;
    private final long roundTimeRemainingSeconds;
    private final boolean baseDestroyed;

    /**
     * @param base                       The base that Team 2 is defending
     * @param roundTimeRemainingSeconds  The time left in the current round
     * @param baseDestroyed             Whether the base has been destroyed
     */
    public BaseDestructionInfo(Base base, long roundTimeRemainingSeconds, boolean baseDestroyed) {
        this.base = base;
        this.roundTimeRemainingSeconds = roundTimeRemainingSeconds;
        this.baseDestroyed = baseDestroyed;
    }

    @Override
    public String getType() {
        return "Base Destruction";
    }

    public Base getBase() {
        return base;
    }

    public long getRoundTimeRemainingSeconds() {
        return roundTimeRemainingSeconds;
    }

    public boolean isBaseDestroyed() {
        return baseDestroyed;
    }
}
