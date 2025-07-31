package com.fullsteam.model;

import com.fullsteam.WeaponFactory;

import java.util.List;

public record WelcomeMessage(String type, String playerId, int team, Long gameId, List<String> weaponOptions) {
    public WelcomeMessage(String playerId, int team, Long gameId) {
        this("welcome", playerId, team, gameId, WeaponFactory.weaponOptions());
    }
}