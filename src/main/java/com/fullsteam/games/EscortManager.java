package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.EscortAIStrategy;
import com.fullsteam.model.gamemodes.EscortGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EscortManager extends AbstractTeamBasedManager {

    private Obstacle payload;

    public EscortManager(GameLobby gameLobby) {
        super(gameLobby);
        startNewRound();
    }

    @Override
    public String gameType() {
        return "Escort";
    }

    @Override
    public void addAIPlayer(int team) {
        String playerId = "ai-" + UUID.randomUUID();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new EscortAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        log.info("AI Player {} (Escort Strategy) joined team {}", playerId, team);
    }

    @Override
    public void startNewRound() {
        obstacles.clear();
        super.startNewRound();
        payload = new Obstacle(Obstacle.createRectangle(
                (Config.GAME_WIDTH - Config.ESCORT_OBSTACLE_WIDTH) / 2,
                (Config.GAME_HEIGHT - Config.ESCORT_OBSTACLE_HEIGHT) / 2,
                Config.ESCORT_OBSTACLE_WIDTH,
                Config.ESCORT_OBSTACLE_HEIGHT).vertices(), false);
    }

    @Override
    protected void updateGame() {
        // 1. Determine payload movement based on last frame's state
        Vector2D payloadCenter = getPayloadCenter();
        double proximitySq = Config.ESCORT_PLAYER_PROXIMITY * Config.ESCORT_PLAYER_PROXIMITY;

        Set<Integer> teamsNearPayload = players.values()
                .stream()
                .filter(p -> !p.isDead())
                .filter(p -> p.getCenter().distanceSquared(payloadCenter) < proximitySq)
                .map(Player::getTeam)
                .collect(Collectors.toSet());

        double moveX = 0D;
        // If only one team is near the payload, it moves.
        // Team 1 (Green) pushes Right (positive X), Team 2 (Red) pushes Left (negative X).
        if (teamsNearPayload.contains(1) && !teamsNearPayload.contains(2)) {
            moveX = Config.ESCORT_OBSTACLE_SPEED;
        } else if (teamsNearPayload.contains(2) && !teamsNearPayload.contains(1)) {
            moveX = -Config.ESCORT_OBSTACLE_SPEED;
        }

        // 2. Update payload to its new position for this frame, checking boundaries
        if (moveX != 0) {
            double finalDelta = moveX;
            Obstacle nextPayload = new Obstacle(payload.vertices()
                    .stream()
                    .map(v -> v.add(new Vector2D(finalDelta, 0)))
                    .toList(), false);

            double minX = nextPayload.vertices().stream().mapToDouble(Vector2D::x).min().orElse(0);
            double maxX = nextPayload.vertices().stream().mapToDouble(Vector2D::x).max().orElse(0);
            if (minX >= 0 && maxX <= Config.GAME_WIDTH) {
                this.payload = nextPayload;
            }
        }

        // 3. Set up obstacles for this frame's physics, making the payload solid
        obstacles.clear();
        obstacles.add(this.payload);

        // 4. Run the main game loop (updates players, bullets, checks collisions)
        super.updateGame();

        obstacles.clear();
    }

    @Override
    public boolean checkEndConditions() {
        double minX = payload.vertices().stream().mapToDouble(Vector2D::x).min().orElse(0D);
        double maxX = payload.vertices().stream().mapToDouble(Vector2D::x).max().orElse(0D);

        // Team 2 (Red) wins by pushing the payload to the left boundary
        if (minX <= 50) {
            sendGameEvent(GameEvent.red("Team 2 has delivered the payload!"));
            team2Score++;
            return true;
            // Team 1 (Green) wins by pushing the payload to the right boundary
        } else if (maxX >= Config.GAME_WIDTH - 50) {
            sendGameEvent(GameEvent.red("Team 1 has delivered the payload!"));
            team1Score++;
            return true;
        }

        // If time runs out, the team that pushed the payload past the centerline wins.
        if (System.currentTimeMillis() >= roundEndTime) {
            sendGameEvent(GameEvent.red("The payload was not delivered in time"));
            return true;
        }
        return false;
    }

    @Override
    protected void generateObstacles() {
        obstacles.clear();
    }

    private Vector2D getPayloadCenter() {
        if (payload == null || payload.vertices().isEmpty()) {
            // A sensible fallback if the payload doesn't exist yet
            return new Vector2D(Config.GAME_WIDTH / 2.0, Config.GAME_HEIGHT / 2.0);
        }
        // Calculate the geometric center by averaging the vertices
        return payload.vertices().stream()
                .reduce(Vector2D.ZERO, Vector2D::add)
                .multiply(1.0 / payload.vertices().size());
    }

    @Override
    protected GameInfo buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        return new EscortGameInfo(
                team1Score,
                team1Score,
                roundTimeRemainingSeconds,
                payload,
                Config.ESCORT_PLAYER_PROXIMITY
        );
    }
}
