package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Hill;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.ai.UnifiedAIStrategy;
import com.fullsteam.model.gamemodes.BlitzInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.micronaut.context.annotation.Prototype;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.KOTH_HILL_KEEP_OUT_RADIUS;
import static com.fullsteam.Config.KOTH_HILL_RADIUS;
import static com.fullsteam.Config.KOTH_POINTS_PER_SECOND;
import static com.fullsteam.Config.KOTH_SCORE_TO_WIN;

/**
 * Manages the game logic for the Blitz mode.
 * Teams must capture opponent's capture points while defending their own.
 * Creates tactical decisions between offense and defense.
 */
@Prototype
public class BlitzManager extends AbstractTeamBasedManager {

    private List<Hill> team1CapturePoints;
    private List<Hill> team2CapturePoints;

    public BlitzManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        initializeCapturePoints();
    }

    @Override
    protected IAIStrategy buildAIStrategy() {
        return new UnifiedAIStrategy();
    }

    /**
     * Initialize capture points on each team's side of the field
     */
    private void initializeCapturePoints() {
        team1CapturePoints = new ArrayList<>();
        team2CapturePoints = new ArrayList<>();

        // Team 1 capture points (left side of map, closer to corners) - owned by Team 1
        double team1X = Config.GAME_WIDTH * 0.15;
        team1CapturePoints.add(new Hill(
                new Vector2D(team1X, Config.GAME_HEIGHT * 0.2),
                KOTH_HILL_RADIUS,
                KOTH_HILL_RADIUS * KOTH_HILL_RADIUS,
                0, // controllingTeam (starts neutral)
                false, // contested
                1 // owningTeam (Team 1 owns these points)
        ));
        team1CapturePoints.add(new Hill(
                new Vector2D(team1X, Config.GAME_HEIGHT * 0.8),
                KOTH_HILL_RADIUS,
                KOTH_HILL_RADIUS * KOTH_HILL_RADIUS,
                0, // controllingTeam (starts neutral)
                false, // contested
                1 // owningTeam (Team 1 owns these points)
        ));

        // Team 2 capture points (right side of map, closer to corners) - owned by Team 2
        double team2X = Config.GAME_WIDTH * 0.85;
        team2CapturePoints.add(new Hill(
                new Vector2D(team2X, Config.GAME_HEIGHT * 0.2),
                KOTH_HILL_RADIUS,
                KOTH_HILL_RADIUS * KOTH_HILL_RADIUS,
                0, // controllingTeam (starts neutral)
                false, // contested
                2 // owningTeam (Team 2 owns these points)
        ));
        team2CapturePoints.add(new Hill(
                new Vector2D(team2X, Config.GAME_HEIGHT * 0.8),
                KOTH_HILL_RADIUS,
                KOTH_HILL_RADIUS * KOTH_HILL_RADIUS,
                0, // controllingTeam (starts neutral)
                false, // contested
                2 // owningTeam (Team 2 owns these points)
        ));
    }

    @Override
    protected boolean checkEndConditions() {
        updateCapturePointControl();

        // Award points for controlling opponent's capture points
        // Team 1 scores by controlling hills owned by Team 2 (opponent hills)
        long team1ControlledOpponentPoints = team2CapturePoints.stream()
                .filter(hill -> hill.controllingTeam() == 1 && !hill.contested())
                .count();

        // Team 2 scores by controlling hills owned by Team 1 (opponent hills)
        long team2ControlledOpponentPoints = team1CapturePoints.stream()
                .filter(hill -> hill.controllingTeam() == 2 && !hill.contested())
                .count();

        // Award points based on number of controlled opponent points
        // Teams can score 1x points for 1 hill, 2x points for both hills
        double pointsThisTick = KOTH_POINTS_PER_SECOND / Config.TICK_RATE;
        team1Score = Math.min(KOTH_SCORE_TO_WIN, team1Score + (team1ControlledOpponentPoints * pointsThisTick));
        team2Score = Math.min(KOTH_SCORE_TO_WIN, team2Score + (team2ControlledOpponentPoints * pointsThisTick));

        // Check for a winner by score or time
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        if (team1Score >= KOTH_SCORE_TO_WIN || team2Score >= KOTH_SCORE_TO_WIN || roundTimerExpired) {
            sendVictoryMessage();
            return true;
        }
        return false;
    }

    /**
     * Updates the control state of all capture points
     */
    private void updateCapturePointControl() {
        List<Player> alivePlayers = entities.getPlayers().stream()
                .filter(p -> !p.isDead())
                .toList();
        // Update Team 1's capture points (Team 2 attacks these)
        team1CapturePoints.replaceAll(hill1 -> updateHillControl(hill1, alivePlayers));
        // Update Team 2's capture points (Team 1 attacks these)
        team2CapturePoints.replaceAll(hill1 -> updateHillControl(hill1, alivePlayers));
    }

    /**
     * Updates control state for a single hill
     */
    private Hill updateHillControl(Hill hill, List<Player> alivePlayers) {
        List<Player> playersOnHill = alivePlayers.stream()
                .filter(p -> {
                    double distanceSq = p.position().distanceSquared(hill.position());
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

        return hill.withState(newControllingTeam, isContested);
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new BlitzInfo(
                this.team1CapturePoints,
                this.team2CapturePoints,
                this.team1Score,
                this.team2Score,
                timeLeft
        );
    }

    @Override
    protected void generateObstacles() {
        generateObstacles(newObstacle -> {
            // Check if the new obstacle intersects with any capture point's keep-out zone
            List<Hill> allCapturePoints = new ArrayList<>();
            allCapturePoints.addAll(team1CapturePoints);
            allCapturePoints.addAll(team2CapturePoints);

            for (Hill hill : allCapturePoints) {
                boolean isColliding = CollisionUtils.checkCirclePolygonCollision(
                        hill.position(),
                        KOTH_HILL_KEEP_OUT_RADIUS,
                        newObstacle.vertices());
                if (isColliding) {
                    return false;
                }
            }
            return true;
        });
    }

    @Override
    public void startNewRound() {
        // Reset all capture points to neutral
        initializeCapturePoints();
        super.startNewRound();
    }
}
