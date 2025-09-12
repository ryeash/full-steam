package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Payload;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.ai.UnifiedAIStrategy;
import com.fullsteam.model.gamemodes.EscortGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.context.annotation.Prototype;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Prototype
public class EscortManager extends AbstractTeamBasedManager {

    private Payload payload;

    public EscortManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        startNewRound();
    }

    @Override
    protected IAIStrategy buildAIStrategy() {
        // Use UnifiedAIStrategy which will automatically discover the Payload objective
        return new UnifiedAIStrategy();
    }

    @Override
    public void startNewRound() {
        super.startNewRound();
        payload = new Payload(
                Obstacle.createRectangle(
                        (Config.GAME_WIDTH - Config.ESCORT_OBSTACLE_WIDTH) / 2,
                        (Config.GAME_HEIGHT - Config.ESCORT_OBSTACLE_HEIGHT) / 2,
                        Config.ESCORT_OBSTACLE_WIDTH,
                        Config.ESCORT_OBSTACLE_HEIGHT).vertices(),
                Config.ESCORT_PLAYER_PROXIMITY,
                50.0, // Left boundary (Team 2's goal)
                Config.GAME_WIDTH - 50.0 // Right boundary (Team 1's goal)
        );
        entities.getObstacles().add(payload);
    }

    @Override
    protected void updateGame(long delta) {
        // Determine payload movement based on last frame's state
        Vector2D payloadCenter = getPayloadCenter();
        double proximitySq = Config.ESCORT_PLAYER_PROXIMITY * Config.ESCORT_PLAYER_PROXIMITY;

        Set<Integer> teamsNearPayload = entities.getPlayers()
                .stream()
                .filter(p -> !p.isDead())
                .filter(p -> p.position().distanceSquared(payloadCenter) < proximitySq)
                .map(Player::getTeam)
                .collect(Collectors.toSet());

        double moveX = 0D;
        // If only one team is near the payload, it moves.
        // Team 1 (Green) pushes Right (positive X), Team 2 (Red) pushes Left (negative X).
        if (teamsNearPayload.contains(1) && !teamsNearPayload.contains(2)) {
            moveX = delta * Config.ESCORT_OBSTACLE_SPEED;
        } else if (teamsNearPayload.contains(2) && !teamsNearPayload.contains(1)) {
            moveX = delta * -Config.ESCORT_OBSTACLE_SPEED;
        }

        // Update payload to its new position for this frame, checking boundaries
        if (moveX != 0) {
            double finalDelta = moveX;
            Payload nextPayload = payload.withVertices(payload.vertices()
                    .stream()
                    .map(v -> v.add(new Vector2D(finalDelta, 0)))
                    .toList());

            double minX = nextPayload.vertices().stream().mapToDouble(Vector2D::x).min().orElse(0);
            double maxX = nextPayload.vertices().stream().mapToDouble(Vector2D::x).max().orElse(0);
            if (minX >= 0 && maxX <= Config.GAME_WIDTH) {
                entities.getObstacles().remove(payload);
                this.payload = nextPayload;
                entities.getObstacles().add(payload);
            }
        }
        super.updateGame(delta);
    }

    @Override
    public boolean checkEndConditions() {
        double minX = payload.vertices().stream().mapToDouble(Vector2D::x).min().orElse(0D);
        double maxX = payload.vertices().stream().mapToDouble(Vector2D::x).max().orElse(0D);

        // Team 2 (Red) wins by pushing the payload to the left boundary
        if (minX <= payload.getLeftBoundary()) {
            sendGameEvent(GameEvent.red("Team 2 has delivered the payload!"));
            team2Score++;
            return true;
            // Team 1 (Green) wins by pushing the payload to the right boundary
        } else if (maxX >= payload.getRightBoundary()) {
            sendGameEvent(GameEvent.red("Team 1 has delivered the payload!"));
            team1Score++;
            return true;
        }

        if (System.currentTimeMillis() >= roundEndTime) {
            sendGameEvent(GameEvent.red("The payload was not delivered in time"));
            return true;
        }
        return false;
    }

    @Override
    protected void generateObstacles() {
        super.generateObstacles(o -> {
            // The obstacle is valid only if it does NOT overlap with the payload corridor.
            // This means the obstacle must be entirely above the corridor OR entirely below it.
            // Define the vertical "keep-out" zone for the payload path.
            // This is the vertical center of the map, plus the payload's height, plus a safety buffer.
            double safetyBuffer = 50.0;
            double pathCorridorHeight = Config.ESCORT_OBSTACLE_HEIGHT + (2 * safetyBuffer);
            double pathTopY = (Config.GAME_HEIGHT / 2.0) - (pathCorridorHeight / 2.0);
            double pathBottomY = (Config.GAME_HEIGHT / 2.0) + (pathCorridorHeight / 2.0);
            double obstacleMinY = o.vertices().stream().mapToDouble(Vector2D::y).min().orElse(0);
            double obstacleMaxY = o.vertices().stream().mapToDouble(Vector2D::y).max().orElse(0);
            boolean isAbove = obstacleMaxY < pathTopY;
            boolean isBelow = obstacleMinY > pathBottomY;
            return isAbove || isBelow;
        });
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
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        return new EscortGameInfo(
                team1Score,
                team2Score,
                timeLeft,
                payload
        );
    }
}
