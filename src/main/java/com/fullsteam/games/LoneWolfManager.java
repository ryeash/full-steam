package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.LoneWolfInfo;
import io.netty.channel.Channel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LoneWolfManager extends AbstractGameStateManager {

    private static final long AI_FILL_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private long lastAIFillCheckTime = 0;

    private String loneWolfId;
    private int loneWolfDeaths = 0;
    private final Set<String> huntersToKill = new HashSet<>();

    public LoneWolfManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Lone Wolf";
    }

    @Override
    protected void startNewRound() {
        // This is called when the game starts
        balanceTeams();
        super.startNewRound();
    }

    @Override
    protected void updateGame() {
        super.updateGame();
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastAIFillCheckTime) > AI_FILL_CHECK_INTERVAL_MS) {
            balanceTeams();
            lastAIFillCheckTime = currentTime;
        }
    }

    private void balanceTeams() {
        if (players.size() >= getMaxPlayers()) {
            return; // Game is full
        }
        int huntersNeeded = (getMaxPlayers() - 1) - (int) players.values().stream().filter(p -> p.getTeam() == 2).count();
        for (int i = 0; i < huntersNeeded; i++) {
            addAIPlayer(2); // Add AI to team 2
        }
    }

    @Override
    public void addAIPlayer(int team) {
        String playerId = "ai-" + UUID.randomUUID();
        // All AI in this mode are hunters with a deathmatch strategy
        AIPlayer player = new AIPlayer(playerId, 0, 0, 2, new DeathmatchAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        huntersToKill.add(playerId); // Add AI to the target list
        log.info("AI Hunter {} joined at position ({}, {})", playerId, player.getX(), player.getY());
    }

    @Override
    public Player addPlayer(String playerId, Channel channel) {
        Player player;
        // The first player to join is the Lone Wolf
        if (loneWolfId == null) {
            this.loneWolfId = playerId;
            player = new Player(playerId, 0, 0, 1); // Team 1
            player.setMaxHealth(Config.DEFAULT_PLAYER_HEALTH * Config.LONE_WOLF_HEALTH_MULTIPLIER);
            player.resetHealth();
            sendGameEvent(GameEvent.red(player.getPlayerName() + " is the Lone Wolf!"));
        } else {
            // Subsequent players are Hunters
            player = new Player(playerId, 0, 0, 2); // Team 2
            huntersToKill.add(playerId);
        }

        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined game {} as {}", playerId, gameId, loneWolfId.equals(playerId) ? "Lone Wolf" : "Hunter");
        return player;
    }

    @Override
    public void removePlayer(String playerId) {
        Player player = players.get(playerId);
        if (player != null) {
            // If a hunter leaves, remove them from the target list.
            if (!player.getId().equals(loneWolfId)) {
                huntersToKill.remove(player.getId());
            }
            // Now, call the parent method to handle the actual removal from the game.
            super.removePlayer(playerId);
        }
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter); // Handle basic kill logic first

        if (victim.getId().equals(loneWolfId)) {
            loneWolfDeaths++;
            Player loneWolf = players.get(loneWolfId);
            if (loneWolf != null) {
                double newDamageMultiplier = 1.0 + (loneWolfDeaths * Config.LONE_WOLF_DAMAGE_BOOST_PER_DEATH);
                loneWolf.setDamageMultiplier(newDamageMultiplier);
                sendGameEvent(GameEvent.red("The Lone Wolf grows stronger! Damage is now " + (int) (newDamageMultiplier * 100) + "%."));
            }
        } else if (shooter != null && shooter.getId().equals(loneWolfId)) {
            // A hunter was killed by the lone wolf
            if (huntersToKill.remove(victim.getId())) {
                sendGameEvent(GameEvent.green("The Lone Wolf has eliminated " + victim.getPlayerName()));
            }
        }
    }

    @Override
    protected boolean checkEndConditions() {
        // Check if the lone wolf has left the game
        if (loneWolfId != null && !players.containsKey(loneWolfId)) {
            sendGameEvent(GameEvent.blue("The Lone Wolf has fled! The Hunters win!"));
            log.info("Game {} ended: Lone Wolf left the game.", gameId);
            return true;
        }

        if (loneWolfDeaths >= 3) {
            sendGameEvent(GameEvent.blue("The Hunters have slain the Lone Wolf! The Hunters win!"));
            log.info("Game {} ended: Lone Wolf defeated.", gameId);
            return true;
        }

        if (huntersToKill.isEmpty() && players.size() > 1) {
            sendGameEvent(GameEvent.red("The Lone Wolf has eliminated all Hunters! The Lone Wolf wins!"));
            log.info("Game {} ended: Lone Wolf wins.", gameId);
            return true;
        }

        return false;
    }

    @Override
    protected void resetDamageMultiplier(Player player) {
        // The Lone Wolf's damage multiplier is persistent and managed separately.
        // We only reset the multiplier for the Hunters.
        if (!player.getId().equals(loneWolfId)) {
            player.setDamageMultiplier(1.0);
        }
    }

    @Override
    protected GameInfo buildGameState() {
        List<String> remainingTargetNames = players.values().stream()
                .filter(p -> huntersToKill.contains(p.getId()))
                .map(Player::getPlayerName)
                .collect(Collectors.toList());
        return new LoneWolfInfo(3 - loneWolfDeaths, remainingTargetNames);
    }
}
