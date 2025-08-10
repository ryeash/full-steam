package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Hazard;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import io.netty.channel.Channel;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fullsteam.Config.ROUND_DURATION_SECONDS;

public abstract class AbstractFreeForAllManager extends AbstractGameStateManager {

    private static final int MAX_PLAYERS = Config.MAX_PLAYERS_PER_TEAM * 2;
    private static final long AI_FILL_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private static final double PLAYER_BUFFER_SPAWN_DISTANCE = 100.0;

    private final AtomicInteger teamIdCounter = new AtomicInteger(100);
    protected long roundEndTime = 0;
    private long lastAIFillCheckTime = 0;

    public AbstractFreeForAllManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        long currentTime = System.currentTimeMillis();
        // Only check to add AI if the round is active
        if (!isRoundOver && currentTime - lastAIFillCheckTime > AI_FILL_CHECK_INTERVAL_MS) {
            balanceOpponents();
            lastAIFillCheckTime = currentTime;
        }
    }

    protected void balanceOpponents() {
        int playersToAdd = MAX_PLAYERS - players.size();
        if (playersToAdd > 0) {
            log.info("Checking to fill game. Current players: {}, Max: {}. Adding {} AI.", players.size(), MAX_PLAYERS, playersToAdd);
            for (int i = 0; i < playersToAdd; i++) {
                addAIPlayer(teamIdCounter.incrementAndGet());
            }
        } else if (playersToAdd < 0) {
            int playersToRemove = -playersToAdd;
            log.info("Too many players. Current players: {}, Max: {}. Removing {} AI.", players.size(), MAX_PLAYERS, playersToRemove);

            // Get a list of AI player IDs to remove
            List<Long> aiPlayerIdsToRemove = players.values().stream()
                    .filter(p -> p instanceof AIPlayer)
                    .map(Player::getId)
                    .limit(playersToRemove)
                    .toList();

            // Remove them
            for (Long playerId : aiPlayerIdsToRemove) {
                removePlayer(playerId);
                log.info("Removed AI Player {} to meet max player limit.", playerId);
            }
        }
    }

    @Override
    public AIPlayer addAIPlayer(int team) {
        Long playerId = Config.ID_COUNTER.incrementAndGet();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new DeathmatchAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        log.info("AI Player {} joined at position ({}, {})", playerId, player.getX(), player.getY());
        return player;
    }

    @Override
    public void startNewRound() {
        this.roundEndTime = System.currentTimeMillis() + (ROUND_DURATION_SECONDS * 1000);
        super.startNewRound();
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() >= roundEndTime) {
            sendVictoryMessage();
            log.info("Round timer has expired. Starting a new round.");
            return true;
        } else {
            return false;
        }
    }

    protected void sendVictoryMessage() {
        players.values().stream()
                .max(Comparator.comparingInt(Player::getKills))
                .ifPresent(winner -> sendGameEvent(GameEvent.blue(winner.getPlayerName() + " wins!")));
    }

    @Override
    public Player addPlayer(long playerId, Channel channel) {
        // If the game is at max capacity, try to remove an AI to make room.
        if (players.size() >= MAX_PLAYERS) {
            Optional<Player> aiToKick = players.values().stream()
                    .filter(p -> p instanceof AIPlayer)
                    .findFirst();

            if (aiToKick.isPresent()) {
                removePlayer(aiToKick.get().getId());
                log.info("Kicking AI player {} to make room for human player {}.", aiToKick.get().getId(), playerId);
            } else {
                // This case means the game is full of human players. The lobby should have prevented this via isFull().
                log.warn("Cannot add player {}: game is full of human players.", playerId);
                return null;
            }
        }

        // In FFA, every player is on their own team.
        int uniqueTeamId = teamIdCounter.getAndIncrement();
        Player player = new Player(playerId, 0, 0, uniqueTeamId);
        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined FFA game {} at position ({}, {})", playerId, gameId, player.getX(), player.getY());
        return player;
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        // In FFA, teams don't matter for spawning, so we can override to spawn anywhere.
        boolean invalidPosition;
        do {
            invalidPosition = false;
            // Spawn anywhere on the map, with padding.
            double x = Config.SPAWN_HORIZONTAL_PADDING + ThreadLocalRandom.current().nextDouble() * (Config.GAME_WIDTH - 2 * Config.SPAWN_HORIZONTAL_PADDING);
            double y = Config.SPAWN_VERTICAL_PADDING + ThreadLocalRandom.current().nextDouble() * (Config.GAME_HEIGHT - 2 * Config.SPAWN_VERTICAL_PADDING);

            player.setX(x);
            player.setY(y);

            if (isColliding(player, obstacles)) {
                invalidPosition = true;
                continue;
            }

            Vector2D playerCenter = new Vector2D(player.getX() + Config.PLAYER_SIZE / 2, player.getY() + Config.PLAYER_SIZE / 2);
            for (Hazard hazard : hazards) {
                if (hazard.type() == Hazard.Type.DAMAGE) {
                    if (playerCenter.distanceSquared(hazard.position()) < hazard.radiusSq()) {
                        invalidPosition = true;
                        break;
                    }
                }
            }

            for (Player other : players.values()) {
                if (!Objects.equals(player.getId(), other.getId()) && other.position().distanceSquared(player.position()) < PLAYER_BUFFER_SPAWN_DISTANCE) {
                    invalidPosition = true;
                    break;
                }
            }
        } while (invalidPosition);
    }
}
