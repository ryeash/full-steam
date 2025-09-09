package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.DeathmatchAIStrategy;
import io.micronaut.websocket.WebSocketSession;

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
    private static final double PLAYER_BUFFER_SPAWN_DISTANCE = 50.0 * 50.0;

    private final AtomicInteger teamIdCounter = new AtomicInteger(100);
    protected long roundEndTime = 0;
    private long lastAIFillCheckTime = 0;

    public AbstractFreeForAllManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
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
        int playersToAdd = MAX_PLAYERS - entities.getPlayers().size();
        if (playersToAdd > 0) {
            log.info("Checking to fill game. Current players: {}, Max: {}. Adding {} AI.", entities.getPlayers().size(), MAX_PLAYERS, playersToAdd);
            for (int i = 0; i < playersToAdd; i++) {
                addAIPlayer(teamIdCounter.incrementAndGet());
            }
        } else if (playersToAdd < 0) {
            int playersToRemove = -playersToAdd;
            log.info("Too many players. Current players: {}, Max: {}. Removing {} AI.", entities.getPlayers().size(), MAX_PLAYERS, playersToRemove);

            // Get a list of AI player IDs to remove
            List<Long> aiPlayerIdsToRemove = entities.getPlayers().stream()
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
        long playerId = Config.ID_COUNTER.incrementAndGet();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new DeathmatchAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        entities.addPlayer(new PlayerSession(this, player, null));
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
        entities.getPlayers().stream()
                .max(Comparator.comparingInt(Player::getKills))
                .ifPresent(winner -> sendGameEvent(GameEvent.blue(winner.getName() + " wins!")));
    }

    @Override
    public PlayerSession addPlayer(long playerId, WebSocketSession channel) {
        // If the game is at max capacity, try to remove an AI to make room.
        if (entities.getPlayers().size() >= MAX_PLAYERS) {
            Optional<Player> aiToKick = entities.getPlayers().stream()
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
        return super.addPlayer(playerId, channel, teamIdCounter.getAndIncrement());
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

            if (physicsEngine.isColliding(player, entities.getObstacles())) {
                invalidPosition = true;
                continue;
            }

            for (Player other : entities.getPlayers()) {
                if (!Objects.equals(player.getId(), other.getId()) && other.position().distanceSquared(player.position()) < PLAYER_BUFFER_SPAWN_DISTANCE) {
                    invalidPosition = true;
                    break;
                }
            }
        } while (invalidPosition);
    }

    @Override
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        // Disallow team changes
        if (request.isRequestTeamChange()) {
            sendGameEvent(GameEvent.yellow("There are no teams in %s!".formatted(buildGameInfo().getType()), playerId));
            request.setRequestTeamChange(false);
        }
        super.handlePlayerConfigChange(playerId, request);
    }
}
