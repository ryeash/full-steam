package com.fullsteam.model;

import com.fullsteam.WeaponFactory;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public record WelcomeMessage(String type,
                             long playerId,
                             int team,
                             Long gameId,
                             List<Obstacle> obstacles,
                             List<String> weaponOptions) {
    public WelcomeMessage(long playerId, int team, Long gameId, List<Obstacle> obstacles) {
        this("welcome", playerId, team, gameId, obstacles, WeaponFactory.weaponOptions());
    }
}