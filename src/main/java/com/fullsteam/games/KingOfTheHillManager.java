package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Hill;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.ai.KingOfTheHillAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.KOTH_HILL_KEEP_OUT_RADIUS;
import static com.fullsteam.Config.KOTH_HILL_RADIUS;
import static com.fullsteam.Config.KOTH_POINTS_PER_SECOND;
import static com.fullsteam.Config.KOTH_SCORE_TO_WIN;
import static com.fullsteam.Config.OBSTACLE_COUNT;

/**
 * Manages the game logic for the King of the Hill mode.
 * The objective is to control a central point to accumulate score.
 */
public class KingOfTheHillManager extends AbstractTeamBasedManager {

    private static final Logger log = LoggerFactory.getLogger(KingOfTheHillManager.class);

    private Hill hill;

    public KingOfTheHillManager(GameLobby gameLobby) {
        super(gameLobby);
        // Create the hill in the center of the map
        Vector2D hillPosition = new Vector2D(Config.GAME_WIDTH / 2.0, Config.GAME_HEIGHT / 2.0);
        this.hill = new Hill(hillPosition, KOTH_HILL_RADIUS, KOTH_HILL_RADIUS * KOTH_HILL_RADIUS, 0, false);
    }

    @Override
    public String gameType() {
        return "King of the Hill";
    }

    @Override
    protected IAIStrategy buildAIStrategy() {
        return new KingOfTheHillAIStrategy();
    }

    @Override
    protected boolean checkEndConditions() {
        updateHillControl();

        // Add points if a team has uncontested control
        if (!hill.contested()) {
            double pointsThisTick = KOTH_POINTS_PER_SECOND / Config.TICK_RATE;
            if (hill.controllingTeam() == 1) {
                team1Score = Math.min(KOTH_SCORE_TO_WIN, team1Score + pointsThisTick);
            } else if (hill.controllingTeam() == 2) {
                team2Score = Math.min(KOTH_SCORE_TO_WIN, team2Score + pointsThisTick);
            }
        }

        // Check for a winner by score or time
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        if (team1Score >= KOTH_SCORE_TO_WIN || team2Score >= KOTH_SCORE_TO_WIN || roundTimerExpired) {
            sendVictoryMessage();
            return true;
        }
        return false;
    }

    /**
     * Checks which players are inside the hill's radius and updates its state.
     */
    private void updateHillControl() {
        List<Player> playersOnHill = players.values().stream()
                .filter(p -> !p.isDead())
                .filter(p -> {
                    double distanceSq = p.getCenter().distanceSquared(hill.position());
                    return distanceSq < hill.radiusSq();
                })
                .toList();

        boolean team1Present = playersOnHill.stream().anyMatch(p -> p.getTeam() == 1);
        boolean team2Present = playersOnHill.stream().anyMatch(p -> p.getTeam() == 2);

        int newControllingTeam = 0;
        boolean isContested = false;

        if (team1Present && !team2Present) {
            newControllingTeam = 1;
        } else if (!team1Present && team2Present) {
            newControllingTeam = 2;
        } else if (team1Present && team2Present) {
            isContested = true;
            newControllingTeam = hill.controllingTeam(); // Keep last controlling team during contest
        }

        // Update the hill state by creating a new immutable record
        this.hill = hill.withState(newControllingTeam, isContested);
    }

    @Override
    protected GameInfo buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new KingOfTheHillInfo(
                this.hill,
                this.team1Score,
                this.team2Score,
                roundTimeRemainingSeconds
        );
    }

    @Override
    protected void updateGame() {
        teamBalancer.balanceTeams(players);
        super.updateGame();
    }

    @Override
    protected void generateObstacles() {
        obstacles.clear();

        for (int i = 0; i < OBSTACLE_COUNT / 2; i++) {
            Obstacle newObstacle;
            boolean isColliding;
            int attempts = 0; // Safety break to prevent infinite loops
            do {
                newObstacle = Obstacle.createRandomPolygonObstacle();
                // Check if the new obstacle intersects with the hill's keep-out zone.
                isColliding = CollisionUtils.checkCirclePolygonCollision(
                        this.hill.position(),
                        KOTH_HILL_KEEP_OUT_RADIUS,
                        newObstacle.vertices());
                attempts++;
            } while (isColliding && attempts < 100); // Keep trying until it's clear or we give up

            if (!isColliding) {
                obstacles.add(newObstacle);
                obstacles.add(newObstacle.create180Clone());
            } else {
                log.warn("Could not place an obstacle without colliding with the hill after 100 attempts.");
            }
        }
        log.info("Generated {} obstacles for King of the Hill, avoiding the central hill area.", obstacles.size());
    }
}