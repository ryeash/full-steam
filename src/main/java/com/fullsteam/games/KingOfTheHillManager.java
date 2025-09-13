package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Hill;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;
import io.micronaut.context.annotation.Prototype;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.KOTH_HILL_KEEP_OUT_RADIUS;
import static com.fullsteam.Config.KOTH_HILL_RADIUS;
import static com.fullsteam.Config.KOTH_POINTS_PER_SECOND;
import static com.fullsteam.Config.KOTH_SCORE_TO_WIN;

/**
 * Manages the game logic for the King of the Hill mode.
 * The objective is to control a central point to accumulate score.
 */
@Prototype
public class KingOfTheHillManager extends AbstractTeamBasedManager {

    private Hill hill;

    public KingOfTheHillManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        // Create the hill in the center of the map
        Vector2D hillPosition = new Vector2D(Config.GAME_WIDTH / 2.0, Config.GAME_HEIGHT / 2.0);
        this.hill = new Hill(hillPosition, KOTH_HILL_RADIUS, KOTH_HILL_RADIUS * KOTH_HILL_RADIUS, 0, false);
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
        List<Player> playersOnHill = entities.getPlayers().stream()
                .filter(p -> !p.isDead())
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

        // Update the hill state by creating a new immutable record
        this.hill = hill.withState(newControllingTeam, isContested);
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new KingOfTheHillInfo(
                this.hill,
                this.team1Score,
                this.team2Score,
                timeLeft
        );
    }

    @Override
    protected void generateObstacles() {
        generateObstacles(newObstacle -> {
            // Check if the new obstacle intersects with the hill's keep-out zone.
            boolean isColliding = CollisionUtils.checkCirclePolygonCollision(
                    this.hill.position(),
                    KOTH_HILL_KEEP_OUT_RADIUS,
                    newObstacle.vertices());
            return !isColliding;
        });
    }
}