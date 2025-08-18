package com.fullsteam.model;

import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;
import java.util.List;

public record GameState(Collection<Player> players,
                        List<Bullet> bullets,
                        List<LaserBlast> laserBlasts,
                        List<FieldEffect> fieldEffects,
                        List<Turret> turrets,
                        List<Vehicle> vehicles,
                        List<Obstacle> obstacles,
                        List<PowerUp> powerUps,
                        long serverTime,
                        GameInfo info) {
}