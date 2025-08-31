package com.fullsteam.model;

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
    private Long lastVehicleActionTime;

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

    public Long getLastVehicleActionTime() {
        return lastVehicleActionTime;
    }

    public void setLastVehicleActionTime(Long lastVehicleActionTime) {
        this.lastVehicleActionTime = lastVehicleActionTime;
    }
}
