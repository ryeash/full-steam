package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.LoneWolfInfo;
import io.netty.channel.Channel;

import java.util.Objects;

import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.RESPAWN_IMMUNITY_DURATION;

@GameName("Lone Wolf")
public class LoneWolfManager extends AbstractGameStateManager {

    private static final long AI_FILL_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private long lastAIFillCheckTime = 0;

    private Long loneWolfId;
    private int loneWolfDeaths = 0;

    public LoneWolfManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected void startNewRound() {
        loneWolfDeaths = 0;
        balanceTeams();
        super.startNewRound();
    }

    /**
     * Disables mid-round respawning. Players will only be brought back to life
     * at the beginning of a new round via startNewRound().
     */
    @Override
    protected void checkAndRespawnPlayers() {
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
        int huntersNeeded = (getMaxPlayers() - 1) - (int) players.values().stream().filter(p -> p.getTeam() == 2).count();
        for (int i = 0; i < huntersNeeded; i++) {
            addAIPlayer(2); // Add AI to team 2
        }
        int wolvesNeeded = 1 - (int) players.values().stream().filter(p -> p.getTeam() == 1).count();
        if (wolvesNeeded > 0) {
            // try to promote a hunter to be the wolf
            // preferentially move a human
            Player toMove = null;
            for (Player value : players.values()) {
                if (value.getTeam() == 2) {
                    toMove = value;
                    if (!(toMove instanceof AIPlayer)) {
                        break;
                    }
                }
            }
            if (toMove != null) {
                killPlayer(toMove, null);
                loneWolfId = toMove.getId();
                applyLoneWolfStatus();
            }
        }
    }

    @Override
    public AIPlayer addAIPlayer(int team) {
        // All AI in this mode are hunters with a deathmatch strategy
        return super.addAIPlayer(2);
    }

    @Override
    public Player addPlayer(long playerId, Channel channel) {
        Player player;
        // The first player to join is the Lone Wolf
        if (loneWolfId == null) {
            this.loneWolfId = playerId;
            player = new Player(playerId, 0, 0, 1); // Team 1
            applyLoneWolfStatus();
        } else {
            // Subsequent players are Hunters
            player = new Player(playerId, 0, 0, 2); // Team 2
        }

        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined game {} as {}", playerId, gameId, loneWolfId.equals(playerId) ? "Lone Wolf" : "Hunter");
        return player;
    }

    @Override
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        Player player = players.get(playerId);
        if (player != null && request.isRequestTeamChange()) {
            if (player.getTeam() == 1) {
                sendGameEvent(GameEvent.team(player.getTeam(), "You may not switch teams! The Wolf must hunt..."));
            } else {
                sendGameEvent(GameEvent.team(player.getTeam(), "You may not switch teams! The Wolf draws near..."));
            }
            return;
        }
        super.handlePlayerConfigChange(playerId, request);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter); // Handle basic kill logic first

        if (Objects.equals(victim.getId(), loneWolfId)) {
            loneWolfDeaths++;
            Player loneWolf = players.get(loneWolfId);
            if (loneWolf != null && loneWolfDeaths < Config.LONE_WOLF_LIVES) {
                double newDamageMultiplier = 1.0 + (loneWolfDeaths * Config.LONE_WOLF_DAMAGE_BOOST_PER_DEATH);
                loneWolf.setDamageMultiplier(newDamageMultiplier);
                loneWolf.setDamageBoostEndTime(Long.MAX_VALUE);
                sendGameEvent(GameEvent.red("The Lone Wolf grows stronger! Damage is now " + (int) (newDamageMultiplier * 100) + "%."));
                for (Player player : players.values()) {
                    player.setDead(false);
                    player.resetHealth();
                    player.finishReload();
                    player.applyArmorUp(RESPAWN_IMMUNITY_DURATION);
                    setValidSpawnPosition(player);
                }
            }
        } else if (shooter != null && Objects.equals(shooter.getId(), loneWolfId)) {
            // A hunter was killed by the lone wolf
            sendGameEvent(GameEvent.green("The Lone Wolf has eliminated " + victim.getPlayerName()));
        }
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundOver = false;
        // Check if the lone wolf has left the game
        if (loneWolfId != null && !players.containsKey(loneWolfId)) {
            sendGameEvent(GameEvent.blue("The Lone Wolf has fled! The Hunters win!"));
            log.info("Game {} ended: Lone Wolf left the game.", gameId);
            roundOver = true;
        }

        if (loneWolfDeaths >= Config.LONE_WOLF_LIVES) {
            sendGameEvent(GameEvent.blue("The Hunters have slain the Lone Wolf! The Hunters win!"));
            log.info("Game {} ended: Lone Wolf defeated.", gameId);
            roundOver = true;
        }

        boolean allHuntersDown = players.values()
                .stream()
                .filter(p -> p.getTeam() == 2)
                .allMatch(Player::isDead);
        if (allHuntersDown && players.size() > 1) {
            sendGameEvent(GameEvent.red("The Lone Wolf has eliminated all Hunters! The Lone Wolf wins!"));
            log.info("Game {} ended: Lone Wolf wins.", gameId);
            roundOver = true;
        }

        if (roundOver) {
            // randomize the wolf again
            if (loneWolfId != null) {
                Player player = players.get(loneWolfId);
                if (player != null) {
                    player.setDamageBoostEndTime(0);
                    player.setMaxHealth(Config.DEFAULT_PLAYER_HEALTH);
                    player.setTeam(2);
                }
            }
        }

        return roundOver;
    }

    @Override
    protected void resetDamageMultiplier(Player player) {
        // The Lone Wolf's damage multiplier is persistent and managed separately.
        // We only reset the multiplier for the Hunters.
        if (!Objects.equals(player.getId(), loneWolfId)) {
            player.setDamageMultiplier(1.0);
        }
    }

    @Override
    protected GameInfo buildGameState() {
        return new LoneWolfInfo(Config.LONE_WOLF_LIVES - loneWolfDeaths);
    }

    @Override
    public int getMaxPlayers() {
        return MAX_PLAYERS_PER_TEAM + 1;
    }

    public void applyLoneWolfStatus() {
        Player player = players.get(loneWolfId);
        if (player != null) {
            player.setTeam(1);
            player.setMaxHealth(Config.DEFAULT_PLAYER_HEALTH * Config.LONE_WOLF_HEALTH_MULTIPLIER);
            player.setDamageMultiplier(1 + (loneWolfDeaths * Config.LONE_WOLF_DAMAGE_BOOST_PER_DEATH));
            player.setDamageBoostEndTime(Long.MAX_VALUE);
            player.resetHealth();
            sendGameEvent(GameEvent.red(player.getPlayerName() + " is the Lone Wolf!"));
        }
    }
}
