package com.fullsteam.model;

import com.fullsteam.WeaponFactory;

import java.util.List;

public record WelcomeMessage(String type, long playerId, int team, Long gameId, List<String> weaponOptions) {
    public WelcomeMessage(long playerId, int team, Long gameId) {
        this("welcome", playerId, team, gameId, WeaponFactory.weaponOptions());
    }
}