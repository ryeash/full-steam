package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.EliminationInfo;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Last Team Standing game mode.
 * Players do not respawn until the round is over.
 * A team scores a point by eliminating all players on the opposing team.
 */
public class EliminationManager extends AbstractTeamBasedManager {

    private static final int SCORE_TO_WIN = 5;
    boolean roundDecided = false;

    public EliminationManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Elimination";
    }

    @Override
    protected void startNewRound() {
        // Reset game state for the new round
        super.startNewRound();
        log.info("Starting new Elimination round. Score: {}-{}", team1Score, team2Score);
    }

    /**
     * Disables mid-round respawning. Players will only be brought back to life
     * at the beginning of a new round via startNewRound().
     */
    @Override
    protected void checkAndRespawnPlayers() {
        // Intentionally left blank.
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        victim.setRespawnTime(-1L);
    }

    @Override
    protected boolean checkEndConditions() {
        // First, check for the overall game win condition.
        if (team1Score >= SCORE_TO_WIN || team2Score >= SCORE_TO_WIN || System.currentTimeMillis() > roundEndTime) {
            sendVictoryMessage();
            return true;
        }

        if (roundDecided) {
            return false;
        }
        // Next, check for the inter-round-ending conditions.
        long team1Alive = players.values().stream().filter(p -> p.getTeam() == 1 && !p.isDead()).count();
        long team2Alive = players.values().stream().filter(p -> p.getTeam() == 2 && !p.isDead()).count();

        int winningTeam = -1;
        if (team1Alive > 0 && team2Alive == 0) {
            team1Score++;
            log.info("Round over! Team 1 wins. Score: {}-{}", team1Score, team2Score);
            roundDecided = true;
            winningTeam = 1;
        } else if (team2Alive > 0 && team1Alive == 0) {
            team2Score++;
            log.info("Round over! Team 2 wins. Score: {}-{}", team1Score, team2Score);
            roundDecided = true;
            winningTeam = 2;
        } else if (team1Alive == 0 && !players.isEmpty()) {
            log.info("Round over! It's a draw. Score remains {}-{}", team1Score, team2Score);
            roundDecided = true;
        }

        if (roundDecided) {
            if (!sentVictoryMessage) {
                if (winningTeam > 0) {
                    sendGameEvent(GameEvent.info("Team %d wins the round!".formatted(winningTeam)));
                } else {
                    sendGameEvent(GameEvent.info("The round is a draw"));
                }
            }
            gameLoop.schedule(this::respawnPlayers, Config.NEXT_ROUND_DELAY_MS, TimeUnit.MILLISECONDS);
        }
        return false;
    }

    private void respawnPlayers() {
        roundDecided = false;
        for (Player player : players.values()) {
            player.setDead(false);
            player.resetHealth();
            player.finishReload();
            setValidSpawnPosition(player);
            log.info("Player {} has respawned.", player.getId());
        }
    }

    @Override
    protected GameState buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        long team1Alive = players.values().stream().filter(p -> p.getTeam() == 1 && !p.isDead()).count();
        long team2Alive = players.values().stream().filter(p -> p.getTeam() == 2 && !p.isDead()).count();

        EliminationInfo gameInfo = new EliminationInfo(
                this.team1Score,
                this.team2Score,
                team1Alive,
                team2Alive,
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