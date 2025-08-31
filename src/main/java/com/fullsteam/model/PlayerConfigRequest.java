package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class PlayerConfigRequest {
    private String name;
    private String weaponName;
    private boolean requestTeamChange;

    public PlayerConfigRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWeaponName() {
        return weaponName;
    }

    public void setWeaponName(String weaponName) {
        this.weaponName = weaponName;
    }

    public boolean isRequestTeamChange() {
        return requestTeamChange;
    }

    public void setRequestTeamChange(boolean requestTeamChange) {
        this.requestTeamChange = requestTeamChange;
    }
}