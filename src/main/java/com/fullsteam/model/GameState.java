package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.core.annotation.Introspected;

import java.util.Collection;
import java.util.List;

@Introspected
public record GameState(Collection<Player> players,
                        List<Bullet> bullets,
                        List<LaserBlast> laserBlasts,
                        List<FieldEffect> fieldEffects,
                        List<Vehicle> vehicles,
                        @JsonIgnore List<Obstacle> obstacles,
                        List<PowerUp> powerUps,
                        long serverTime,
                        GameInfo info) {
}