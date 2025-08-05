package com.fullsteam.model;

import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;
import java.util.List;

public record GameState(Collection<Player> players,
                        List<Bullet> bullets,
                        List<Explosion> explosions,
                        List<PoisonCloud> poisonClouds,
                        List<Obstacle> obstacles,
                        List<Hazard> hazards,
                        List<DeathMarker> deathMarkers,
                        List<GameEvent> events,
                        List<PowerUp> powerUps,
                        long serverTime,
                        GameInfo info) {
}