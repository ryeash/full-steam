package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import io.netty.channel.Channel;

import java.util.Objects;
import java.util.UUID;

public class GunMasterManager extends FreeForAllManager {

    private static final long WEAPON_SWITCH_INTERVAL_MS = 20_000; // 20 seconds
    private long nextWeaponSwitchTime = 0;
    private Weapon currentGlobalWeapon;

    public GunMasterManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Gun Master";
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
    protected void updateGame() {
        super.updateGame();
        long currentTime = System.currentTimeMillis();
        if (!isRoundOver && currentTime >= nextWeaponSwitchTime) {
            forceWeaponSwitch();
            this.nextWeaponSwitchTime = currentTime + WEAPON_SWITCH_INTERVAL_MS;
        }
    }

    private void forceWeaponSwitch() {
        if (this.currentGlobalWeapon != null) {
            Weapon current = this.currentGlobalWeapon;
            // don't switch to the same weapon
            while (Objects.equals(current.getName(), this.currentGlobalWeapon.getName())) {
                this.currentGlobalWeapon = WeaponFactory.getRandomWeapon();
            }
        }
        this.currentGlobalWeapon = WeaponFactory.getRandomWeapon();
        sendGameEvent(GameEvent.blue("Weapon switched to: " + this.currentGlobalWeapon.getName()));
        for (Player player : players.values()) {
            player.setWeapon(this.currentGlobalWeapon);
        }
    }

    @Override
    public Player addPlayer(String playerId, Channel channel) {
        Player player = super.addPlayer(playerId, channel);
        if (player != null) {
            // Ensure new players get the current weapon
            if (currentGlobalWeapon != null) {
                player.setWeapon(currentGlobalWeapon);
            }
        }
        return player;
    }

    @Override
    public void addAIPlayer(int team) {
        String playerId = "ai-" + UUID.randomUUID();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new DeathmatchAIStrategy(), AIArchetype.randomArchetype());
        if (currentGlobalWeapon != null) {
            player.setWeapon(currentGlobalWeapon);
        }
        setValidSpawnPosition(player);
        players.put(playerId, player);
        log.info("AI Player {} joined Gun Master at position ({}, {})", playerId, player.getX(), player.getY());
    }

    @Override
    public void handlePlayerConfigChange(String playerId, PlayerConfigRequest request) {
        Player player = players.get(playerId);
        if (player == null) {
            return;
        }

        // Allow name changes
        if (request.getPlayerName() != null && !request.getPlayerName().isEmpty()) {
            player.setPlayerName(request.getPlayerName());
        }

        // Disallow weapon changes
        if (request.getWeaponName() != null && !request.getWeaponName().isEmpty()) {
            sendGameEvent(GameEvent.team(player.getTeam(), "Weapon selection is disabled in Gun Master!"));
        }

        // Disallow team changes
        if (request.isRequestTeamChange()) {
            sendGameEvent(GameEvent.team(player.getTeam(), "There are no teams in Gun Master!"));
        }
        log.info("Player {} reconfigured: name={}", playerId, player.getPlayerName());
    }
}