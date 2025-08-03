package com.fullsteam.model;

public class PlayerConfigRequest {
    private String playerName;
    private String weaponName;
    private boolean requestTeamChange;

    public PlayerConfigRequest() {
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
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