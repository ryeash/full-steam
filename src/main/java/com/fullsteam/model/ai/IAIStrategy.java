package com.fullsteam.model.ai;

import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;

import static com.fullsteam.Config.VISION_RANGE;

/**
 * Defines the contract for a game-mode-specific AI behavior.
 */
public interface IAIStrategy {
    /**
     * Analyzes the game state and tells the AI what to do.
     *
     * @param self       The AIPlayer instance being controlled.
     * @param allPlayers All players in the game.
     * @param gameInfo   The current game mode's state.
     */
    void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo);

    default Player findClosestEnemy(AIPlayer self, Collection<Player> allPlayers) {
        Player closestEnemy = null;
        double minDistanceSq = VISION_RANGE * VISION_RANGE;

        for (Player other : allPlayers) {
            // Skip self, teammates, or dead players
            if (other.getId().equals(self.getId()) || other.getTeam() == self.getTeam() || other.isDead()) {
                continue;
            }

            double dx = other.getX() - self.getX();
            double dy = other.getY() - self.getY();
            double distanceSq = dx * dx + dy * dy;

            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestEnemy = other;
            }
        }
        return closestEnemy;
    }

    default boolean isInRange(AIPlayer self, Player target, double range) {
        if (target == null) {
            return false;
        }
        double rangeSq = range * range;
        return self.getCenter().distanceSq(target.getCenter()) < rangeSq;
    }
}