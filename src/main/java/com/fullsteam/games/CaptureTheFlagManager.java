package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Flag;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.CaptureTheFlagAIStrategy;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.CTF_BASE_AREA_PADDING;
import static com.fullsteam.Config.CTF_FLAG_PICKUP_RADIUS;
import static com.fullsteam.Config.CTF_FLAG_RETURN_TIMEOUT_MS;
import static com.fullsteam.Config.CTF_SCORE_TO_WIN;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;


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
    protected IAIStrategy buildAIStrategy() {
        return new CaptureTheFlagAIStrategy();
    }

    @Override
    protected void startNewRound() {
        randomizeBaseLocations();
        super.startNewRound();
        this.team1Flag = team1Flag.asReturned();
        this.team2Flag = team2Flag.asReturned();
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        updateFlags();
    }

    private void updateFlags() {
        // Update positions of carried flags
        if (team1Flag.state() == Flag.FlagState.CARRIED) {
            Player carrier = entities.getPlayer(team1Flag.carrierId());
            if (carrier != null && !carrier.isDead()) {
                team1Flag = team1Flag.withPosition(carrier.position());
            } else { // Carrier disconnected or died without killPlayer catching it
                team1Flag = team1Flag.asDroppedAt(team1Flag.position());
            }
        }
        if (team2Flag.state() == Flag.FlagState.CARRIED) {
            Player carrier = entities.getPlayer(team2Flag.carrierId());
            if (carrier != null && !carrier.isDead()) {
                team2Flag = team2Flag.withPosition(carrier.position());
            } else {
                team2Flag = team2Flag.asDroppedAt(team2Flag.position());
            }
        }

        // Check for automatic flag returns
        if (team1Flag.state() == Flag.FlagState.DROPPED && System.currentTimeMillis() - team1Flag.dropTimestamp() > CTF_FLAG_RETURN_TIMEOUT_MS) {
            team1Flag = team1Flag.asReturned();
            sendGameEvent(GameEvent.team(1, "Team 1 flag returned"));
        }
        if (team2Flag.state() == Flag.FlagState.DROPPED && System.currentTimeMillis() - team2Flag.dropTimestamp() > CTF_FLAG_RETURN_TIMEOUT_MS) {
            team2Flag = team2Flag.asReturned();
            sendGameEvent(GameEvent.team(2, "Team 2 flag returned"));
        }

        // Check for player interactions with flags
        for (Player player : entities.getPlayers()) {
            if (player.isDead()) {
                continue;
            }

            // --- Check for flag captures (scoring) ---
            if (player.getTeam() == 1 && team2Flag.carrierId() != null && team2Flag.carrierId().equals(player.getId())) {
                if (team1Flag.state() == Flag.FlagState.AT_BASE && player.position().distanceSquared(team1Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Score++;
                    sendGameEvent(GameEvent.team(1, String.format("%s scored for Team 1!", player.getPlayerName())));
                    team2Flag = team2Flag.asReturned();
                }
            }
            if (player.getTeam() == 2 && team1Flag.carrierId() != null && team1Flag.carrierId().equals(player.getId())) {
                if (team2Flag.state() == Flag.FlagState.AT_BASE && player.position().distanceSquared(team2Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Score++;
                    sendGameEvent(GameEvent.team(2, String.format("%s scored for Team 2!", player.getPlayerName())));
                    team1Flag = team1Flag.asReturned();
                }
            }

            // --- Check for flag pickups ---
            // Team 1 player interactions
            if (player.getTeam() == 1) {
                // Pick up enemy flag from base
                if (team2Flag.state() == Flag.FlagState.AT_BASE && player.position().distanceSquared(team2Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Flag = team2Flag.asCarriedBy(player.getId());
                    sendGameEvent(GameEvent.team(1, String.format("Team 1 took the flag! (%s)", player.getPlayerName())));
                }
                // Pick up enemy flag when dropped
                if (team2Flag.state() == Flag.FlagState.DROPPED && player.position().distanceSquared(team2Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Flag = team2Flag.asCarriedBy(player.getId());
                    sendGameEvent(GameEvent.team(2, "Team 2's flag was picked up!"));
                }
                // Return friendly flag when dropped
                if (team1Flag.state() == Flag.FlagState.DROPPED && player.position().distanceSquared(team1Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Flag = team1Flag.asReturned();
                    sendGameEvent(GameEvent.team(1, String.format("Team 1's flag was returned by %s!", player.getPlayerName())));
                }
            }
            // Team 2 player interactions
            else if (player.getTeam() == 2) {
                if (team1Flag.state() == Flag.FlagState.AT_BASE && player.position().distanceSquared(team1Flag.basePosition()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Flag = team1Flag.asCarriedBy(player.getId());
                    sendGameEvent(GameEvent.team(2, String.format("Team 2 took the flag! (%s)", player.getPlayerName())));
                }
                if (team1Flag.state() == Flag.FlagState.DROPPED && player.position().distanceSquared(team1Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team1Flag = team1Flag.asCarriedBy(player.getId());
                    sendGameEvent(GameEvent.team(1, "Team 1's flag was picked up!"));
                }
                if (team2Flag.state() == Flag.FlagState.DROPPED && player.position().distanceSquared(team2Flag.position()) < FLAG_PICKUP_RADIUS_SQ) {
                    team2Flag = team2Flag.asReturned();
                    sendGameEvent(GameEvent.team(2, String.format("Team 2's flag was returned by %s!", player.getPlayerName())));
                }
            }
        }
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        // Check if the victim was carrying a flag
        if (team1Flag.state() == Flag.FlagState.CARRIED && Objects.equals(victim.getId(), team1Flag.carrierId())) {
            team1Flag = team1Flag.asDroppedAt(victim.position());
            sendGameEvent(GameEvent.team(1, "Team 1's flag was dropped!"));
        }
        if (team2Flag.state() == Flag.FlagState.CARRIED && Objects.equals(victim.getId(), team2Flag.carrierId())) {
            team2Flag = team2Flag.asDroppedAt(victim.position());
            sendGameEvent(GameEvent.team(2, "Team 2's flag was dropped!"));
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
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new CaptureTheFlagInfo(
                this.team1Flag,
                this.team2Flag,
                this.team1Score,
                this.team2Score,
                timeLeft
        );
    }

    @Override
    protected void generateObstacles() {
        generateObstacles(newObstacle -> {
            boolean isColliding = CollisionUtils.checkCirclePolygonCollision(team1Flag.basePosition(), CTF_FLAG_PICKUP_RADIUS, newObstacle.vertices()) ||
                                  CollisionUtils.checkCirclePolygonCollision(team2Flag.basePosition(), CTF_FLAG_PICKUP_RADIUS, newObstacle.vertices());
            return !isColliding;
        });
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