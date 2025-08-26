package com.fullsteam;

import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.model.Player;
import com.fullsteam.model.ai.AIPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;

/**
 * Ensures teams are balanced with a mix of human and AI players up to a maximum limit.
 * It prioritizes keeping human players, removing AI players if a team is over capacity.
 */
public class TeamBalancer {
    private static final Logger logger = LoggerFactory.getLogger(TeamBalancer.class);
    private final AbstractGameStateManager gameStateManager;

    public TeamBalancer(AbstractGameStateManager gameStateManager) {
        this.gameStateManager = gameStateManager;
    }

    /**
     * Checks the current player counts on each team and adds or removes AI players as needed.
     *
     * @param players The current map of all players in the game.
     */
    public void balanceTeams(Collection<Player> players) {
        // Count players on each team
        long team1Count = players.stream().filter(p -> p.getTeam() == 1).count();
        long team2Count = players.stream().filter(p -> p.getTeam() == 2).count();

        // Balance Team 1
        balanceTeam(1, team1Count, MAX_PLAYERS_PER_TEAM, players);

        // Balance Team 2
        balanceTeam(2, team2Count, MAX_PLAYERS_PER_TEAM, players);
    }

    public void balanceTeam(int teamId, long currentTeamSize, long targetTeamSize, Collection<Player> players) {
        if (currentTeamSize < targetTeamSize) {
            // Add AI players to fill the team
            long playersToAdd = targetTeamSize - currentTeamSize;
            if (playersToAdd > 0) {
                logger.debug("Team {} is under capacity. Adding {} AI player(s).", teamId, playersToAdd);
                for (int i = 0; i < playersToAdd; i++) {
                    gameStateManager.addAIPlayer(teamId);
                }
            }
        } else if (currentTeamSize > targetTeamSize) {
            // Remove AI players if team is over capacity (e.g., a human joined)
            List<Player> aiPlayersOnTeam = players.stream()
                    .filter(p -> p.getTeam() == teamId && p instanceof AIPlayer)
                    .toList();

            long playersToRemove = currentTeamSize - targetTeamSize;
            if (playersToRemove > 0 && !aiPlayersOnTeam.isEmpty()) {
                logger.debug("Team {} is over capacity. Removing {} AI player(s) to make room.", teamId, playersToRemove);
                for (int i = 0; i < playersToRemove && i < aiPlayersOnTeam.size(); i++) {
                    gameStateManager.removePlayer(aiPlayersOnTeam.get(i).getId());
                }
            }
        }
    }
}