package com.fullsteam.model.ai;

import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;

public class DeathmatchAIStrategy implements IAIStrategy {
    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();

        // --- Pre-computation: Get key state information once ---
        Player closestEnemy = findClosestEnemy(self, allPlayers);

        // --- Archetype-based Decision Making ---
        // The AI's "personality" determines its priorities, even in a simple deathmatch.
        switch (self.archetype()) {
            case WARRIOR:
            case OBJECTIVE_HOUND: // In Deathmatch, the objective *is* combat.
                // These archetypes are purely aggressive. They will always seek a fight.
                prioritizeCombat(self, closestEnemy);
                break;

            case GUARDIAN:
                // The Guardian is more cautious and values self-preservation.
                prioritizeDefense(self, closestEnemy);
                break;

            case BALANCED:
            default:
                // The default, balanced behavior.
                runBalancedLogic(self, closestEnemy);
                break;
        }
    }

    /**
     * The default behavior: find the nearest enemy and attack.
     */
    private void runBalancedLogic(AIPlayer self, Player closestEnemy) {
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
        } else {
            // No enemies in sight, so wander to find one.
            self.setCurrentState(AIPlayer.AIState.WANDERING);
        }
    }

    /**
     * Warrior/Objective Hound: Pure, relentless aggression.
     */
    private void prioritizeCombat(AIPlayer self, Player closestEnemy) {
        // For this mode, the most aggressive strategy is the same as the balanced one.
        runBalancedLogic(self, closestEnemy);
    }

    /**
     * Guardian: Engages in combat but will retreat if badly wounded.
     */
    private void prioritizeDefense(AIPlayer self, Player closestEnemy) {
        // Priority 1: If health is low and an enemy is nearby, run away!
        boolean isWounded = self.getCurrentHealth() < self.getMaxHealth() * 0.4; // Flee below 40% health
        if (isWounded && closestEnemy != null) {
            self.setCurrentState(AIPlayer.AIState.FLEEING);
            self.setObjectiveTargetPoint(closestEnemy.getCenter());
            return;
        }

        // Priority 2: If not wounded, engage in combat as normal.
        runBalancedLogic(self, closestEnemy);
    }
}