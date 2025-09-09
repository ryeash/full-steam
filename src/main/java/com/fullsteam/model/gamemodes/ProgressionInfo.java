package com.fullsteam.model.gamemodes;

import com.fullsteam.model.WeaponUpgrade;

import java.util.List;

/**
 * Contains the game-state information specific to the Progression game mode.
 * Players start with weak weapons and upgrade by collecting drops from defeated enemies.
 */
public final class ProgressionInfo extends FreeForAllInfo {
    private final List<WeaponUpgrade> weaponUpgrades; // Using Object to avoid circular dependency

    public ProgressionInfo(long timeLeft, List<WeaponUpgrade> weaponUpgrades) {
        super(timeLeft);
        this.weaponUpgrades = weaponUpgrades;
    }

    public List<WeaponUpgrade> getWeaponUpgrades() {
        return weaponUpgrades;
    }

    @Override
    public String getType() {
        return "Progression";
    }
}
