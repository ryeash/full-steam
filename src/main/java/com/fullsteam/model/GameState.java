package com.fullsteam.model;

import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;
import java.util.List;

public record GameState(Collection<Player> players,
                        List<Bullet> bullets,
                        List<Explosion> explosions,
                        List<PoisonCloud> poisonClouds,
                        List<Turret> turrets,
                        List<Obstacle> obstacles,
                        List<Hazard> hazards,
                        List<PowerUp> powerUps,
                        long serverTime,
                        GameInfo info) {
}