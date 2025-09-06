package com.fullsteam.model;

import com.fullsteam.WeaponFactory;
import com.fullsteam.games.AbstractGameStateManager;
import io.micronaut.websocket.WebSocketSession;

/**
 * Player session information
 */
public final class PlayerSession {
    private final AbstractGameStateManager game;
    private final WebSocketSession session;
    private final Player player;
    private PlayerInput input;
    private Long lastAltActionTime;
    private Weapon turretWeapons = WeaponFactory.TURRET_WEAPONS.getFirst();

    public PlayerSession(AbstractGameStateManager game, Player player, WebSocketSession session) {
        this.game = game;
        this.player = player;
        this.session = session;
    }

    public AbstractGameStateManager getGame() {
        return game;
    }

    public Long getPlayerId() {
        return player.id();
    }

    public WebSocketSession getSession() {
        return session;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerInput getInput() {
        return input;
    }

    public PlayerSession setInput(PlayerInput input) {
        this.input = input;
        return this;
    }

    public Long getLastAltActionTime() {
        return lastAltActionTime;
    }

    public void setLastAltActionTime(Long lastAltActionTime) {
        this.lastAltActionTime = lastAltActionTime;
    }

    public Weapon getTurretWeapons() {
        return turretWeapons;
    }

    public Weapon cycleTurretWeapons() {
        int i = WeaponFactory.TURRET_WEAPONS.indexOf(turretWeapons) + 1;
        if (i >= WeaponFactory.TURRET_WEAPONS.size()) {
            i = 0;
        }
        setTurretWeapons(WeaponFactory.TURRET_WEAPONS.get(i));
        return getTurretWeapons();
    }

    public void setTurretWeapons(Weapon turretWeapons) {
        this.turretWeapons = turretWeapons;
    }
}
