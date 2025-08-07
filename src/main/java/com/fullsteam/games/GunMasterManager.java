package com.fullsteam.games;

import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.gamemodes.FreeForAllInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.GunMasterInfo;
import io.netty.channel.Channel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GunMasterManager extends AbstractFreeForAllManager {

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
                sendGameEvent(GameEvent.yellow("Weapon switched to: " + this.currentGlobalWeapon.getName(), playerId));
                player.setWeapon(currentGlobalWeapon);
            }
        }
        return player;
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
    public void handlePlayerConfigChange(String playerId, PlayerConfigRequest request) {
        Player player = players.get(playerId);
        if (player == null) {
            return;
        }
        // Disallow weapon changes
        if (request.getWeaponName() != null && !request.getWeaponName().isEmpty()) {
            sendGameEvent(GameEvent.team(player.getTeam(), "Weapon selection is disabled in Gun Master!"));
            return;
        }

        // Disallow team changes
        if (request.isRequestTeamChange()) {
            sendGameEvent(GameEvent.team(player.getTeam(), "There are no teams in Gun Master!"));
            return;
        }
        super.handlePlayerConfigChange(playerId, request);
    }


    @Override
    protected GameInfo buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        List<FreeForAllInfo.PlayerScore> playerScores = players.values().stream()
                .sorted(Comparator.comparingInt(Player::getKills).reversed()) // Sort by kills descending
                .map(player -> new FreeForAllInfo.PlayerScore(player.getPlayerName(), player.getKills()))
                .collect(Collectors.toList());

        return new GunMasterInfo(
                playerScores,
                roundTimeRemainingSeconds);
    }
}