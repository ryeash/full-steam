package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Flag;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.CaptureTheFlagAIStrategy;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.CTF_BASE_AREA_PADDING;
import static com.fullsteam.Config.CTF_FLAG_PICKUP_RADIUS;
import static com.fullsteam.Config.CTF_FLAG_RETURN_TIMEOUT_MS;
import static com.fullsteam.Config.CTF_SCORE_TO_WIN;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.OBSTACLE_COUNT;
import static com.fullsteam.model.GameEvent.EventType.FLAG_RETURN;

public class CaptureTheFlagManager extends AbstractTeamBasedManager {

    private static final Logger log = LoggerFactory.getLogger(CaptureTheFlagManager.class);

    private static final double FLAG_PICKUP_RADIUS_SQ = CTF_FLAG_PICKUP_RADIUS * CTF_FLAG_PICKUP_RADIUS;

    private Flag team1Flag;
    private Flag team2Flag;

    public CaptureTheFlagManager(GameLobby gameLobby) {
        super(gameLobby);
        log.info("Capture the Flag game mode initialized.");
        randomizeBaseLocations();
    }

    @Override
    public String gameType() {
        return "Capture the Flag";
    }

    @Override
    public void addAIPlayer(int team) {
        String playerId = "ai-" + UUID.randomUUID();
        // Inject the CTF-specific strategy when creating the AI
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new CaptureTheFlagAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        log.info("AI Player {} (CTF Strategy) joined team {}", playerId, team);
    }

    @Override
    protected void startNewRound() {
        randomizeBaseLocations();
        super.startNewRound();
        this.team1Flag = team1Flag.asReturned();
        this.team2Flag = team2Flag.asReturned();
    }

    @Override
    protected void updateGame() {
        super.updateGame();
        updateFlags();
    }

    private void updateFlags() {
        // Update positions of carried flags
        if (team1Flag.state() == Flag.FlagState.CARRIED) {
            Player carrier = players.get(team1Flag.carrierId());
            if (carrier != null && !carrier.isDead()) {
                team1Flag = team1Flag.withPosition(carrier.getCenter());
            } else { // Carrier disconnected or died without killPlayer catching it
                team1Flag = team1Flag.asDroppedAt(team1Flag.position());
            }
        }
        if (team2Flag.state() == Flag.FlagState.CARRIED) {
            Player carrier = players.get(team2Flag.carrierId());
            if (carrier != null && !carrier.isDead()) {
                team2Flag = team2Flag.withPosition(carrier.getCenter());
            } else {
                team2Flag = team2Flag.asDroppedAt(team2Flag.position());
            }
        }

        // Check for automatic flag returns
        if (team1Flag.state() == Flag.FlagState.DROPPED && System.currentTimeMillis() - team1Flag.dropTimestamp() > CTF_FLAG_RETURN_TIMEOUT_MS) {
            log.info("Team 1's flag returned to base automatically.");
            team1Flag = team1Flag.asReturned();
            sendGameEvent(GameEvent.withType("Team 1 flag returned", FLAG_RETURN));
        }
        if (team2Flag.state() == Flag.FlagState.DROPPED && System.currentTimeMillis() - team2Flag.dropTimestamp() > CTF_FLAG_RETURN_TIMEOUT_MS) {
            log.info("Team 2's flag returned to base automatically.");
            team2Flag = team2Flag.asReturned();
            sendGameEvent(GameEvent.withType("Team 2 flag returned", FLAG_RETURN));
        }

        // Check for player interactions with flags
        for (Player player : players.values()) {
            if (player.isDead()) {
                continue;
            }

            // --- Check for flag captures (scoring) ---
            if (player.getTeam() == 1 && team2Flag.carrierId() != null && team2Flag.carrierId().equals(player.getId())) {
                if (team1Flag.state() == Flag.FlagState.AT_BASE && Vector2D.distanceSq(player.getCenter(), team1Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Score++;
                    log.info("Team 1 scores! Score: {}-{}", team1Score, team2Score);
                    sendGameEvent(GameEvent.withType(String.format("%s scored for Team 1!", player.getPlayerName()), GameEvent.EventType.FLAG_CAPTURE));
                    team2Flag = team2Flag.asReturned();
                }
            }
            if (player.getTeam() == 2 && team1Flag.carrierId() != null && team1Flag.carrierId().equals(player.getId())) {
                if (team2Flag.state() == Flag.FlagState.AT_BASE && Vector2D.distanceSq(player.getCenter(), team2Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Score++;
                    log.info("Team 2 scores! Score: {}-{}", team1Score, team2Score);
                    sendGameEvent(GameEvent.withType(String.format("%s scored for Team 2!", player.getPlayerName()), GameEvent.EventType.FLAG_CAPTURE));
                    team1Flag = team1Flag.asReturned();
                }
            }

            // --- Check for flag pickups ---
            // Team 1 player interactions
            if (player.getTeam() == 1) {
                // Pick up enemy flag from base
                if (team2Flag.state() == Flag.FlagState.AT_BASE && Vector2D.distanceSq(player.getCenter(), team2Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Flag = team2Flag.asCarriedBy(player.getId());
                    log.info("Player {} from team 1 picked up team 2's flag!", player.getPlayerName());
                    sendGameEvent(GameEvent.withType(String.format("Team 1 took the flag! (%s)", player.getPlayerName()), GameEvent.EventType.FLAG_PICKUP));
                }
                // Pick up enemy flag when dropped
                if (team2Flag.state() == Flag.FlagState.DROPPED && Vector2D.distanceSq(player.getCenter(), team2Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Flag = team2Flag.asCarriedBy(player.getId());
                    log.info("Player {} from team 1 recovered the dropped enemy flag!", player.getPlayerName());
                    sendGameEvent(GameEvent.withType("Team 2's flag was picked up!", FLAG_RETURN));
                }
                // Return friendly flag when dropped
                if (team1Flag.state() == Flag.FlagState.DROPPED && Vector2D.distanceSq(player.getCenter(), team1Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Flag = team1Flag.asReturned();
                    log.info("Player {} from team 1 returned their flag to base!", player.getPlayerName());
                    sendGameEvent(GameEvent.withType(String.format("Team 1's flag was returned by %s!", player.getPlayerName()), FLAG_RETURN));
                }
            }
            // Team 2 player interactions
            else if (player.getTeam() == 2) {
                if (team1Flag.state() == Flag.FlagState.AT_BASE && Vector2D.distanceSq(player.getCenter(), team1Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Flag = team1Flag.asCarriedBy(player.getId());
                    log.info("Player {} from team 2 picked up team 1's flag!", player.getPlayerName());
                    sendGameEvent(GameEvent.withType(String.format("Team 2 took the flag! (%s)", player.getPlayerName()), GameEvent.EventType.FLAG_PICKUP));
                }
                if (team1Flag.state() == Flag.FlagState.DROPPED && Vector2D.distanceSq(player.getCenter(), team1Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Flag = team1Flag.asCarriedBy(player.getId());
                    log.info("Player {} from team 2 recovered the dropped enemy flag!", player.getPlayerName());
                    sendGameEvent(GameEvent.withType("Team 1's flag was picked up!", FLAG_RETURN));
                }
                if (team2Flag.state() == Flag.FlagState.DROPPED && Vector2D.distanceSq(player.getCenter(), team2Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Flag = team2Flag.asReturned();
                    log.info("Player {} from team 2 returned their flag to base!", player.getPlayerName());
                    sendGameEvent(GameEvent.withType(String.format("Team 2's flag was returned by %s!", player.getPlayerName()), FLAG_RETURN));
                }
            }
        }
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        // Check if the victim was carrying a flag
        if (team1Flag.state() == Flag.FlagState.CARRIED && victim.getId().equals(team1Flag.carrierId())) {
            team1Flag = team1Flag.asDroppedAt(victim.getCenter());
            log.info("Team 1's flag carrier was eliminated! Flag dropped at ({}, {}).", victim.getX(), victim.getY());
            sendGameEvent(GameEvent.withType("Team 1's flag was dropped!", GameEvent.EventType.FLAG_DROP));
        }
        if (team2Flag.state() == Flag.FlagState.CARRIED && victim.getId().equals(team2Flag.carrierId())) {
            team2Flag = team2Flag.asDroppedAt(victim.getCenter());
            log.info("Team 2's flag carrier was eliminated! Flag dropped at ({}, {}).", victim.getX(), victim.getY());
            sendGameEvent(GameEvent.withType("Team 2's flag was dropped!", GameEvent.EventType.FLAG_DROP));
        }
        super.killPlayer(victim, shooter); // Handle the rest of the death logic
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        if (team1Score >= CTF_SCORE_TO_WIN || team2Score >= CTF_SCORE_TO_WIN || roundTimerExpired) {
            sendVictoryMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected GameInfo buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new CaptureTheFlagInfo(
                this.team1Flag,
                this.team2Flag,
                this.team1Score,
                this.team2Score,
                roundTimeRemainingSeconds
        );
    }

    @Override
    protected void generateObstacles() {
        obstacles.clear();
        for (int i = 0; i < OBSTACLE_COUNT / 2; i++) {
            Obstacle newObstacle;
            boolean isColliding;
            int attempts = 0;
            do {
                newObstacle = Obstacle.createRandomPolygonObstacle();
                // Check collision with both flag bases
                isColliding = CollisionUtils.checkCirclePolygonCollision(team1Flag.basePosition(), CTF_FLAG_PICKUP_RADIUS, newObstacle.vertices()) ||
                              CollisionUtils.checkCirclePolygonCollision(team2Flag.basePosition(), CTF_FLAG_PICKUP_RADIUS, newObstacle.vertices());
                attempts++;
            } while (isColliding && attempts < 100);

            if (!isColliding) {
                obstacles.add(newObstacle);
                obstacles.add(newObstacle.create180Clone());
            } else {
                log.warn("Could not place an obstacle without colliding with a flag base after 100 attempts.");
            }
        }
        log.info("Generated {} obstacles for Capture the Flag, avoiding flag bases.", obstacles.size());
    }

    private void randomizeBaseLocations() {
        // --- Randomize flag base positions with point symmetry ---
        // Generate a random position for Team 1's base in the left half of the map,
        // respecting the defined padding to avoid placing it too close to the edges or center.
        double team1X = ThreadLocalRandom.current().nextDouble(CTF_BASE_AREA_PADDING, (GAME_WIDTH / 2.0) - CTF_BASE_AREA_PADDING);
        double team1Y = ThreadLocalRandom.current().nextDouble(CTF_BASE_AREA_PADDING, GAME_HEIGHT - CTF_BASE_AREA_PADDING);
        Vector2D team1Base = new Vector2D(team1X, team1Y);

        // Team 2's base is a mirror image of Team 1's base through the center of the map,
        // ensuring the layout is always fair and symmetric.
        Vector2D team2Base = new Vector2D(GAME_WIDTH - team1X, GAME_HEIGHT - team1Y);

        this.team1Flag = new Flag(1, Flag.FlagState.AT_BASE, team1Base, team1Base, null, 0);
        this.team2Flag = new Flag(2, Flag.FlagState.AT_BASE, team2Base, team2Base, null, 0);
    }
}