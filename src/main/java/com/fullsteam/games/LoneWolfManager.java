package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.LoneWolfInfo;
import com.fullsteam.systems.PlayerManager;
import io.netty.channel.Channel;

import java.util.Objects;

import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.RESPAWN_IMMUNITY_DURATION;

public class LoneWolfManager extends AbstractGameStateManager {

    private static final long AI_FILL_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private long lastAIFillCheckTime = 0;

    private Long loneWolfId;
    private int loneWolfDeaths = 0;

    public LoneWolfManager(GameLobby gameLobby) {
        super(gameLobby);

        this.playerManager = new PlayerManager(playerManager) {
            // The Lone Wolf's damage multiplier is persistent and managed separately.
            // We only reset the multiplier for the Hunters.
            @Override
            protected void resetDamageMultiplier(Player player) {
                if (!Objects.equals(player.getId(), loneWolfId)) {
                    player.setDamageMultiplier(1.0);
                }
            }
        };

    }

    @Override
    protected void startNewRound() {
        loneWolfDeaths = 0;
        balanceTeams();
        super.startNewRound();
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastAIFillCheckTime) > AI_FILL_CHECK_INTERVAL_MS) {
            balanceTeams();
            lastAIFillCheckTime = currentTime;
        }
    }

    private void balanceTeams() {
        int huntersNeeded = (getMaxPlayers() - 1) - (int) entities.getPlayers().values().stream().filter(p -> p.getTeam() == 2).count();
        for (int i = 0; i < huntersNeeded; i++) {
            addAIPlayer(2); // Add AI to team 2
        }
        int wolvesNeeded = 1 - (int) entities.getPlayers().values().stream().filter(p -> p.getTeam() == 1).count();
        if (wolvesNeeded > 0) {
            // try to promote a hunter to be the wolf
            // preferentially move a human
            Player toMove = null;
            for (Player value : entities.getPlayers().values()) {
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
        // The first player to join is the Lone Wolf
        Player player;
        if (loneWolfId == null) {
            this.loneWolfId = playerId;
            player = super.addPlayer(playerId, channel, 1);
            applyLoneWolfStatus();
        } else {
            // Subsequent entities.getPlayers() are Hunters
            player = super.addPlayer(playerId, channel, 2);
        }
        return player;
    }

    @Override
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        Player player = entities.getPlayers().get(playerId);
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
            Player loneWolf = entities.getPlayers().get(loneWolfId);
            if (loneWolf != null && loneWolfDeaths < Config.LONE_WOLF_LIVES) {
                double newDamageMultiplier = 1.0 + (loneWolfDeaths * Config.LONE_WOLF_DAMAGE_BOOST_PER_DEATH);
                loneWolf.setDamageMultiplier(newDamageMultiplier);
                loneWolf.setDamageBoostEndTime(Long.MAX_VALUE);
                sendGameEvent(GameEvent.red("The Lone Wolf grows stronger! Damage is now " + (int) (newDamageMultiplier * 100) + "%."));
                vehicleManager.resetVehicles();
                for (Player player : entities.getPlayers().values()) {
                    player.setDead(false);
                    player.resetHp();
                    player.finishReload();
                    player.setVehicleId(null);
                    player.applyArmorUp(RESPAWN_IMMUNITY_DURATION);
                    setValidSpawnPosition(player);
                }
            }
        } else if (shooter != null && Objects.equals(shooter.getId(), loneWolfId)) {
            // A hunter was killed by the lone wolf
            victim.setRespawnTime(-1);
            sendGameEvent(GameEvent.green("The Lone Wolf has eliminated " + victim.getPlayerName()));
        }
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundOver = false;
        // Check if the lone wolf has left the game
        if (loneWolfId != null && !entities.getPlayers().containsKey(loneWolfId)) {
            sendGameEvent(GameEvent.blue("The Lone Wolf has fled! The Hunters win!"));
            log.info("Game {} ended: Lone Wolf left the game.", gameId);
            roundOver = true;
        }

        if (loneWolfDeaths >= Config.LONE_WOLF_LIVES) {
            sendGameEvent(GameEvent.blue("The Hunters have slain the Lone Wolf! The Hunters win!"));
            log.info("Game {} ended: Lone Wolf defeated.", gameId);
            roundOver = true;
        }

        boolean allHuntersDown = entities.getPlayers().values()
                .stream()
                .filter(p -> p.getTeam() == 2)
                .allMatch(Player::isDead);
        if (allHuntersDown && entities.getPlayers().size() > 1) {
            sendGameEvent(GameEvent.red("The Lone Wolf has eliminated all Hunters! The Lone Wolf wins!"));
            log.info("Game {} ended: Lone Wolf wins.", gameId);
            roundOver = true;
        }

        if (roundOver) {
            // randomize the wolf again
            if (loneWolfId != null) {
                Player player = entities.getPlayers().get(loneWolfId);
                if (player != null) {
                    player.setDamageBoostEndTime(0);
                    player.setMaxHp(Config.DEFAULT_PLAYER_HEALTH);
                    player.setTeam(2);
                }
            }
        }

        return roundOver;
    }

    @Override
    protected GameInfo buildGameInfo() {
        return new LoneWolfInfo(Config.LONE_WOLF_LIVES - loneWolfDeaths);
    }

    @Override
    public int getMaxPlayers() {
        return MAX_PLAYERS_PER_TEAM + 1;
    }

    public void applyLoneWolfStatus() {
        Player player = entities.getPlayers().get(loneWolfId);
        if (player != null) {
            player.setTeam(1);
            player.setMaxHp(Config.DEFAULT_PLAYER_HEALTH * Config.LONE_WOLF_HEALTH_MULTIPLIER);
            player.setDamageMultiplier(1 + (loneWolfDeaths * Config.LONE_WOLF_DAMAGE_BOOST_PER_DEATH));
            player.setDamageBoostEndTime(Long.MAX_VALUE);
            player.resetHp();
            sendGameEvent(GameEvent.red(player.getPlayerName() + " is the Lone Wolf!"));
        }
    }
}
