package com.fullsteam.model;

import com.fullsteam.model.gamemodes.GameInfo;

import java.util.List;

public record GameState(List<Player> players,
                        List<Bullet> bullets,
                        List<Obstacle> obstacles,
                        List<DeathMarker> deathMarkers,
                        List<GameEvent> events,
                        GameInfo info) {
}