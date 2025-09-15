package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.StockBattleInfo;
import io.micronaut.context.annotation.Prototype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Stock Battle game mode manager.
 * Teams have limited revives (stock) and must eliminate the enemy team completely.
 * Each death consumes one stock point. When a team's stock reaches zero,
 * their players cannot revive anymore. Victory is achieved by eliminating
 * all enemy players when their stock is depleted.
 */
@Prototype
public class StockBattleManager extends AbstractTeamBasedManager {

    private static final Logger log = LoggerFactory.getLogger(StockBattleManager.class);

    // Configuration constants
    private static final int INITIAL_STOCK_PER_TEAM = 20;
    private static final int STOCK_WARNING_THRESHOLD = 3;

    // Team stock tracking
    private int team1Stock;
    private int team2Stock;

    // Tracking for warnings
    private boolean team1StockWarningShown = false;
    private boolean team2StockWarningShown = false;

    public StockBattleManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
        log.info("Stock Battle game mode initialized with {} stock per team.", INITIAL_STOCK_PER_TEAM);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();

        // Reset stock for both teams
        team1Stock = INITIAL_STOCK_PER_TEAM;
        team2Stock = INITIAL_STOCK_PER_TEAM;

        // Reset warning flags
        team1StockWarningShown = false;
        team2StockWarningShown = false;

        sendGameEvent(GameEvent.yellow("Stock Battle: Each team has " + INITIAL_STOCK_PER_TEAM + " lives!"));
        sendGameEvent(GameEvent.info("Eliminate the enemy team when their stock reaches zero to win!"));

        log.info("Stock Battle round started. Team 1 Stock: {}, Team 2 Stock: {}", team1Stock, team2Stock);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);


        // Consume stock when a player dies
        if (victim.getTeam() == 1) {
            int startTeam1Stock = team1Stock;
            team1Stock = Math.max(0, team1Stock - 1);
            log.info("Team 1 player {} died. Team 1 stock reduced to {}", victim.getName(), team1Stock);

            if (team1Stock == 0) {
                if (startTeam1Stock == 1) {
                    sendGameEvent(GameEvent.team(2, "Team 1 is out of stock! Eliminate remaining players to win!"));
                }
                // Prevent further respawning for this team
                victim.setRespawnTime(-1L);
            } else if (team1Stock <= STOCK_WARNING_THRESHOLD && !team1StockWarningShown) {
                sendGameEvent(GameEvent.team(1, "Warning: Team 1 only has " + team1Stock + " lives remaining!"));
                team1StockWarningShown = true;
            }
        } else if (victim.getTeam() == 2) {
            int startTeam2Stock = team2Stock;
            team2Stock = Math.max(0, team2Stock - 1);
            log.info("Team 2 player {} died. Team 2 stock reduced to {}", victim.getName(), team2Stock);

            if (team2Stock == 0) {
                if (startTeam2Stock == 1) {
                    sendGameEvent(GameEvent.team(1, "Team 2 is out of stock! Eliminate remaining players to win!"));
                }
                // Prevent further respawning for this team
                victim.setRespawnTime(-1L);
            } else if (team2Stock <= STOCK_WARNING_THRESHOLD && !team2StockWarningShown) {
                sendGameEvent(GameEvent.team(2, "Warning: Team 2 only has " + team2Stock + " lives remaining!"));
                team2StockWarningShown = true;
            }
        }
    }

    @Override
    protected boolean checkEndConditions() {
        // Count alive players for each team
        long team1Alive = entities.getPlayers().stream()
                .filter(p -> p.getTeam() == 1 && !p.isDead())
                .count();
        long team2Alive = entities.getPlayers().stream()
                .filter(p -> p.getTeam() == 2 && !p.isDead())
                .count();

        // Check for elimination victory conditions
        boolean team1Eliminated = (team1Stock == 0 && team1Alive == 0);
        boolean team2Eliminated = (team2Stock == 0 && team2Alive == 0);

        if (team1Eliminated && team2Eliminated) {
            // Both teams eliminated simultaneously - draw
            if (!sentVictoryMessage) {
                sendGameEvent(GameEvent.info("Draw! Both teams eliminated simultaneously!"));
                sentVictoryMessage = true;
            }
            return true;
        } else if (team1Eliminated) {
            // Team 2 wins
            if (!sentVictoryMessage) {
                sendGameEvent(GameEvent.team(2, "Team 2 wins! Team 1 eliminated!"));
                sentVictoryMessage = true;
            }
            return true;
        } else if (team2Eliminated) {
            // Team 1 wins
            if (!sentVictoryMessage) {
                sendGameEvent(GameEvent.team(1, "Team 1 wins! Team 2 eliminated!"));
                sentVictoryMessage = true;
            }
            return true;
        }

        // Check for time limit
        if (System.currentTimeMillis() >= roundEndTime) {
            if (!sentVictoryMessage) {
                // Determine winner by remaining stock + alive players
                int team1Total = team1Stock + (int) team1Alive;
                int team2Total = team2Stock + (int) team2Alive;

                if (team1Total > team2Total) {
                    sendGameEvent(GameEvent.team(1, "Team 1 wins by stock advantage! (" + team1Total + " vs " + team2Total + ")"));
                } else if (team2Total > team1Total) {
                    sendGameEvent(GameEvent.team(2, "Team 2 wins by stock advantage! (" + team2Total + " vs " + team1Total + ")"));
                } else {
                    sendGameEvent(GameEvent.info("Draw! Equal stock remaining!"));
                }
                sentVictoryMessage = true;
            }
            return true;
        }

        return false;
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        // Count alive players for each team
        int team1Alive = (int) entities.getPlayers().stream()
                .filter(p -> p.getTeam() == 1 && !p.isDead())
                .count();
        int team2Alive = (int) entities.getPlayers().stream()
                .filter(p -> p.getTeam() == 2 && !p.isDead())
                .count();

        return new StockBattleInfo(
                team1Stock,
                team2Stock,
                team1Alive,
                team2Alive,
                timeLeft
        );
    }
}
