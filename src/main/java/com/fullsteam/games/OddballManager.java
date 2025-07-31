package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Oddball;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.OddballAIStrategy;
import com.fullsteam.model.gamemodes.OddballInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OddballManager extends AbstractTeamBasedManager {

    private static final double SCORE_TO_WIN = 150.0;
    private static final double POINTS_PER_SECOND = 1.0;
    private static final double BALL_PICKUP_RADIUS = 30.0;
    private static final double BALL_PICKUP_RADIUS_SQ = BALL_PICKUP_RADIUS * BALL_PICKUP_RADIUS;
    private static final long BALL_RESET_TIMEOUT_MS = 15_000; // 15 seconds

    private Oddball oddball;
    private final Vector2D ballSpawnPoint = new Vector2D(Config.GAME_WIDTH / 2.0, Config.GAME_HEIGHT / 2.0);

    public OddballManager(GameLobby gameLobby) {
        super(gameLobby);
        this.oddball = new Oddball(Oddball.OddballState.ON_SPAWN, ballSpawnPoint, null, 0);
    }

    @Override
    public String gameType() {
        return "Oddball";
    }

    @Override
    public void addAIPlayer(int team) {
        String playerId = "ai-" + UUID.randomUUID();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new OddballAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        log.info("AI Player {} (Oddball Strategy) joined team {}", playerId, team);
    }

    @Override
    protected void fireWeapon(Player player, double aimAngle) {
        if (Objects.equals(oddball.carrierId(), player.getId())) {
            // the oddball carrier can't shoot
            return;
        }
        super.fireWeapon(player, aimAngle);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();
        this.oddball = oddball.asReset(ballSpawnPoint);
        team1Score = 0;
        team2Score = 0;
    }

    @Override
    protected void updateGame() {
        super.updateGame();
        updateOddball();
    }


    private void updateOddball() {
        // --- Add points to the carrying team ---
        if (oddball.state() == Oddball.OddballState.CARRIED) {
            Player carrier = players.get(oddball.carrierId());
            if (carrier != null && !carrier.isDead()) {
                double pointsThisTick = POINTS_PER_SECOND / Config.TICK_RATE;
                if (carrier.getTeam() == 1) {
                    team1Score = Math.min(SCORE_TO_WIN, team1Score + pointsThisTick);
                } else {
                    team2Score = Math.min(SCORE_TO_WIN, team2Score + pointsThisTick);
                }
                // Ball moves with the carrier
                oddball = oddball.withPosition(carrier.getCenter());
            } else {
                oddball = oddball.asDroppedAt(oddball.position());
                sendGameEvent(GameEvent.info("The Oddball was dropped!"));
            }
        }

        // --- Check for automatic ball reset ---
        if (oddball.state() == Oddball.OddballState.DROPPED && System.currentTimeMillis() - oddball.dropTimestamp() > BALL_RESET_TIMEOUT_MS) {
            log.info("Oddball returned to spawn automatically.");
            oddball = oddball.asReset(ballSpawnPoint);
            sendGameEvent(GameEvent.info("Oddball reset to center."));
        }

        // --- Check for player pickups ---
        if (oddball.state() == Oddball.OddballState.ON_SPAWN || oddball.state() == Oddball.OddballState.DROPPED) {
            for (Player player : players.values()) {
                if (player.isDead()) {
                    continue;
                }

                if (Vector2D.distanceSq(player.getCenter(), oddball.position()) < BALL_PICKUP_RADIUS_SQ) {
                    oddball = oddball.asCarriedBy(player.getId(), player.getCenter());
                    log.info("Player {} picked up the Oddball for team {}!", player.getPlayerName(), player.getTeam());
                    sendGameEvent(GameEvent.info("%s picked up the Oddball!".formatted(player.getPlayerName())));
                    break; // Only one player can pick it up
                }
            }
        }
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        // Check if the victim was carrying the ball
        if (oddball.state() == Oddball.OddballState.CARRIED && victim.getId().equals(oddball.carrierId())) {
            oddball = oddball.asDroppedAt(victim.getCenter());
            log.info("Oddball carrier was eliminated! Ball dropped at ({}, {}).", victim.getX(), victim.getY());
            sendGameEvent(GameEvent.info("The Oddball carrier was eliminated!"));
        }
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        if (team1Score >= SCORE_TO_WIN || team2Score >= SCORE_TO_WIN || roundTimerExpired) {
            sendVictoryMessage();
            return true;
        }
        return false;
    }

    @Override
    protected GameState buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        OddballInfo gameInfo = new OddballInfo(
                this.oddball,
                this.team1Score,
                this.team2Score,
                roundTimeRemainingSeconds
        );

        return new GameState(
                List.copyOf(players.values()),
                List.copyOf(bullets),
                List.copyOf(obstacles),
                List.copyOf(deathMarkers),
                List.copyOf(gameEvents),
                gameInfo
        );
    }
}