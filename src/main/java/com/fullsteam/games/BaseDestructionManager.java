package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Base;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.gamemodes.BaseDestructionInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.websocket.WebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_HEALTH;
import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_RADIUS;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;

/**
 * Base Destruction game mode.
 * Team 1 (Attackers) tries to destroy Team 2's (Defenders) base before time runs out.
 * If the base is destroyed, Team 1 wins. If time runs out with the base intact, Team 2 wins.
 */
@Prototype
public class BaseDestructionManager extends AbstractTeamBasedManager {

    private static final Logger log = LoggerFactory.getLogger(BaseDestructionManager.class);

    private Base defendingBase;
    private boolean baseDestroyed = false;
    private boolean sent10SecondWarning = false;

    public BaseDestructionManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        generateBasePosition();
    }

    @Override
    public PlayerSession addPlayer(long playerId, WebSocketSession channel) {
        // In Base Destruction mode, Team 1 are attackers, Team 2 are defenders
        // Try to balance teams but prefer defenders (Team 2) if both teams are equal
        long team1Count = entities.getPlayers().stream().filter(p -> p.getTeam() == 1).count();
        long team2Count = entities.getPlayers().stream().filter(p -> p.getTeam() == 2).count();

        int team;
        if (team1Count < Config.MAX_PLAYERS_PER_TEAM && team2Count < Config.MAX_PLAYERS_PER_TEAM) {
            // If both teams have space, slightly favor defenders (Team 2)
            team = (team2Count <= team1Count) ? 2 : 1;
        } else if (team1Count < Config.MAX_PLAYERS_PER_TEAM) {
            team = 1; // Only Team 1 has space
        } else if (team2Count < Config.MAX_PLAYERS_PER_TEAM) {
            team = 2; // Only Team 2 has space
        } else {
            // Both teams are full, assign randomly
            team = ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        }

        return addPlayer(playerId, channel, team);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();
        baseDestroyed = false;
        sent10SecondWarning = false;
        generateBasePosition();

        // Send role announcements
        sendGameEvent(GameEvent.team(1, "Attackers: DESTROY the enemy base before time runs out!"));
        sendGameEvent(GameEvent.team(2, "Defenders: DEFEND your base until time runs out!"));
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        updateBase();
    }

    private void updateBase() {
        if (defendingBase != null && !baseDestroyed && defendingBase.isDestroyed()) {
            fieldEffectSystem.addFieldEffect(new Explosion(
                    defendingBase.getX(),
                    defendingBase.getY(),
                    0,
                    0,
                    defendingBase.getRadius(),
                    1000,
                    300));
            baseDestroyed = true;
        }
    }

    @Override
    protected void populateSpatialGrids() {
        super.populateSpatialGrids();
        if (defendingBase != null && !defendingBase.isDestroyed()) {
            double size = defendingBase.getRadius() * 2;
            entities.getTargetGrid().insert(defendingBase,
                    defendingBase.getX() - defendingBase.getRadius(),
                    defendingBase.getY() - defendingBase.getRadius(),
                    size, size);
        }
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        if (baseDestroyed) {
            // Team 1 (Attackers) win
            if (!sentVictoryMessage) {
                team1Score++;
                sendVictoryMessage();
            }
            return true;
        } else if (roundTimerExpired) {
            // Team 2 (Defenders) win by successfully defending
            if (!sentVictoryMessage) {
                team2Score++;
                sendGameEvent(GameEvent.team(2, "TIME'S UP! Defenders successfully defended the base!"));
                sendVictoryMessage();
            }
            return true;
        }

        // Send 10-second warning
        if (!sent10SecondWarning && (roundEndTime - System.currentTimeMillis()) <= 10000) {
            sent10SecondWarning = true;
            sendGameEvent(GameEvent.red("10 seconds remaining! Defenders, hold the line!"));
        }

        return false;
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new BaseDestructionInfo(defendingBase, timeLeft, baseDestroyed);
    }

    @Override
    protected void generateObstacles() {
        entities.getObstacles().clear();

        // Create a defensive wall with three segments and two gaps
        generateDefensiveWall();

        // Add a few random obstacles on the attacker's side for cover
        generateAttackerCover();
    }

    private void generateDefensiveWall() {
        // Wall positioned between the center line and the base
        double wallX = GAME_WIDTH * 0.6; // Position wall at 60% across the map
        double wallThickness = 40;
        double gapSize = 80;

        // Calculate positions for three wall segments with two gaps
        double totalWallArea = GAME_HEIGHT - (2 * Config.SPAWN_VERTICAL_PADDING);
        double segmentHeight = (totalWallArea - (2 * gapSize)) / 3;

        double startY = Config.SPAWN_VERTICAL_PADDING;

        // Create three wall segments
        for (int i = 0; i < 3; i++) {
            double segmentY = startY + i * (segmentHeight + gapSize);
            Obstacle wallSegment = Obstacle.createRectangle(
                    wallX - wallThickness / 2,
                    segmentY,
                    wallThickness,
                    segmentHeight
            );
            entities.getObstacles().add(wallSegment);
        }
    }

    private void generateAttackerCover() {
        // Add some cover obstacles on the attacker's side (left half)
        int coverCount = 3 + ThreadLocalRandom.current().nextInt(3); // 3-5 cover obstacles
        int maxRetries = 20;

        for (int i = 0; i < coverCount; i++) {
            int retries = 0;
            while (retries < maxRetries) {
                // Generate obstacles only on the attacker's side (left half)
                double x = Config.SPAWN_HORIZONTAL_PADDING +
                           ThreadLocalRandom.current().nextDouble((double) GAME_WIDTH / 2 - Config.SPAWN_HORIZONTAL_PADDING * 2);
                double y = Config.SPAWN_VERTICAL_PADDING +
                           ThreadLocalRandom.current().nextDouble(GAME_HEIGHT - Config.SPAWN_VERTICAL_PADDING * 2);

                // Create a smaller rectangular cover obstacle
                double width = 40 + ThreadLocalRandom.current().nextDouble(40); // 60-100 width
                double height = 60 + ThreadLocalRandom.current().nextDouble(30); // 40-70 height

                Obstacle coverObstacle = Obstacle.createRectangle(x, y, width, height);

                // Check if it overlaps with existing obstacles
                boolean overlaps = entities.getObstacles().stream().anyMatch(existing ->
                        CollisionUtils.checkObstacleOverlap(coverObstacle, existing, 30));

                if (!overlaps) {
                    entities.getObstacles().add(coverObstacle);
                    break;
                }
                retries++;
            }
        }
    }

    private void generateBasePosition() {
        // Center the base on Team 2's (defenders) side of the map
        double baseX = GAME_WIDTH - (GAME_WIDTH / 4.0); // 3/4 across the map
        double baseY = GAME_HEIGHT / 2.0; // Centered vertically

        Vector2D basePosition = new Vector2D(baseX, baseY);
        this.defendingBase = new Base(
                Config.ID_COUNTER.incrementAndGet(),
                basePosition,
                BASE_DESTRUCTION_BASE_RADIUS,
                BASE_DESTRUCTION_BASE_HEALTH,
                2 // Team 2 (Defenders)
        );

        log.info("Generated base at position ({}, {}) with {} health",
                baseX, baseY, BASE_DESTRUCTION_BASE_HEALTH);
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        boolean invalidPosition;
        do {
            invalidPosition = false;
            double x, y;

            if (player.getTeam() == 1) {
                // Team 1 (Attackers) spawn on the left side
                double spawnableWidth = (GAME_WIDTH / 2.0) - Config.SPAWN_HORIZONTAL_PADDING - Config.SPAWN_MIDFIELD_BUFFER;
                x = Config.SPAWN_HORIZONTAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableWidth;
            } else {
                // Team 2 (Defenders) spawn near their base on the right side, but not too close to the base
                double minX = (GAME_WIDTH / 2.0) + Config.SPAWN_MIDFIELD_BUFFER;
                double maxX = GAME_WIDTH - Config.SPAWN_HORIZONTAL_PADDING;
                x = minX + ThreadLocalRandom.current().nextDouble() * (maxX - minX);
            }

            double spawnableHeight = GAME_HEIGHT - (2 * Config.SPAWN_VERTICAL_PADDING);
            y = Config.SPAWN_VERTICAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableHeight;

            player.setX(x);
            player.setY(y);

            // Check if spawn point is inside an obstacle
            if (physicsEngine.isColliding(player, entities.getObstacles())) {
                invalidPosition = true;
                continue;
            }

            // For Team 2 (Defenders), ensure they don't spawn too close to their own base
            if (player.getTeam() == 2 && defendingBase != null) {
                double distanceToBase = player.position().distance(defendingBase.position());
                if (distanceToBase < BASE_DESTRUCTION_BASE_RADIUS + 40) { // 40 pixels minimum distance
                    invalidPosition = true;
                }
            }
        } while (invalidPosition);
    }

    @Override
    protected void sendVictoryMessage() {
        if (sentVictoryMessage) {
            return;
        }
        sentVictoryMessage = true;

        if (baseDestroyed) {
            sendGameEvent(GameEvent.team(1, "Attackers win!"));
        } else {
            sendGameEvent(GameEvent.team(2, "Defenders win!"));
        }
    }
}
