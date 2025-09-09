package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.GunMasterInfo;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.websocket.WebSocketSession;

import java.util.Objects;

@Prototype
public class GunMasterManager extends AbstractFreeForAllManager {

    private static final long WEAPON_SWITCH_INTERVAL_MS = 20_000; // 20 seconds
    private long nextWeaponSwitchTime = 0;
    private Weapon currentGlobalWeapon;

    public GunMasterManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    public void startNewRound() {
        super.startNewRound();
        // Set the first weapon switch time
        this.nextWeaponSwitchTime = System.currentTimeMillis() + WEAPON_SWITCH_INTERVAL_MS;
        // Select the initial weapon
        forceWeaponSwitch();
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        long currentTime = System.currentTimeMillis();
        if (!isRoundOver && currentTime >= nextWeaponSwitchTime) {
            forceWeaponSwitch();
            this.nextWeaponSwitchTime = currentTime + WEAPON_SWITCH_INTERVAL_MS;
        }
    }

    private void forceWeaponSwitch() {
        if (this.currentGlobalWeapon != null) {
            // don't switch to the same weapon
            Weapon next;
            do {
                next = WeaponFactory.getRandomWeapon();
            } while (Objects.equals(next.getName(), this.currentGlobalWeapon.getName()));
            this.currentGlobalWeapon = next;
        } else {
            this.currentGlobalWeapon = WeaponFactory.getRandomWeapon();
        }
        sendGameEvent(GameEvent.blue("Weapon switched to: " + this.currentGlobalWeapon.getName()));
        for (Player player : entities.getPlayers()) {
            player.setWeapon(this.currentGlobalWeapon);
        }
    }

    @Override
    public PlayerSession addPlayer(long playerId, WebSocketSession channel) {
        PlayerSession session = super.addPlayer(playerId, channel);
        if (session != null) {
            // Ensure new players get the current weapon
            if (currentGlobalWeapon != null) {
                sendGameEvent(GameEvent.yellow("Weapon switched to: " + this.currentGlobalWeapon.getName(), playerId));
                session.getPlayer().setWeapon(currentGlobalWeapon);
            }
        }
        return session;
    }

    @Override
    public AIPlayer addAIPlayer(int team) {
        AIPlayer aiPlayer = super.addAIPlayer(team);
        if (currentGlobalWeapon != null) {
            aiPlayer.setWeapon(currentGlobalWeapon);
        }
        return aiPlayer;
    }

    @Override
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        // Disallow weapon changes
        if (request.getWeaponName() != null && !request.getWeaponName().isEmpty()) {
            sendGameEvent(GameEvent.yellow("Weapon selection is disabled!", playerId));
            request.setWeaponName(currentGlobalWeapon.getName());
        }
        super.handlePlayerConfigChange(playerId, request);
    }

    @Override
    protected GameInfo buildGameInfo() {
        return new GunMasterInfo(Math.max(0, (roundEndTime - System.currentTimeMillis()) / 1000));
    }
}