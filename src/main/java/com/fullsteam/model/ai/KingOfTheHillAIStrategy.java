package com.fullsteam.model.ai;

import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;

import java.util.Collection;
import java.util.Objects;

public class KingOfTheHillAIStrategy implements IAIStrategy {

    // Use a static final instance for a safe fallback.
    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();
        GameInfo gameInfo = gameState.info();

        if (!(gameInfo instanceof KingOfTheHillInfo koth)) {
            // Fallback safely instead of crashing the server if the game mode is wrong.
            FALLBACK_STRATEGY.updateAIState(self, gameState);
            return;
        }

        // --- Archetype-based Decision Making ---
        switch (self.archetype()) {
            case WARRIOR:
                prioritizeCombat(self, allPlayers, koth);
                break;
            case GUARDIAN:
                prioritizeDefense(self, allPlayers, koth);
                break;
            case OBJECTIVE_HOUND:
                prioritizeObjective(self, allPlayers, koth);
                break;
            case BALANCED:
            default:
                runBalancedLogic(self, allPlayers, koth);
                break;
        }
    }

    /**
     * A well-rounded strategy that serves as the default behavior.
     */
    private void runBalancedLogic(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // Priority 1: Hunt enemies on or near the hill first!
        Player enemyOnHill = findClosestEnemyOnHill(self, allPlayers, koth);
        if (enemyOnHill != null) {
            self.setCurrentTarget(enemyOnHill);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: The hill is contested or not ours. CAPTURE!
        if (koth.getHill().controllingTeam() != self.getTeam() || koth.getHill().contested()) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(koth.getHill().position());
            return;
        }

        // Priority 3: An enemy is nearby but not on hill. ATTACK if close enough!
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null && isInRange(self, closestEnemy, 400)) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 4: The hill is ours and secure. DEFEND! (Patrol the area)
        self.setCurrentState(AIPlayer.AIState.WANDERING);
        self.setObjectiveTargetPoint(koth.getHill().position());
    }

    /**
     * Warrior: Prioritizes hunting hill enemies, then general combat, then objectives.
     */
    private void prioritizeCombat(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // Even warriors prioritize enemies on the hill
        Player enemyOnHill = findClosestEnemyOnHill(self, allPlayers, koth);
        if (enemyOnHill != null) {
            self.setCurrentTarget(enemyOnHill);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // The Warrior's #2 priority is always to fight anyone else.
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }
        // If no one is around to fight, it will play the objective as a tertiary goal.
        runBalancedLogic(self, allPlayers, koth);
    }

    /**
     * Guardian: Prioritizes holding the hill and defending it.
     */
    private void prioritizeDefense(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // Priority #1: Hunt enemies on the hill with extreme prejudice!
        Player enemyOnHill = findClosestEnemyOnHill(self, allPlayers, koth);
        if (enemyOnHill != null) {
            self.setCurrentTarget(enemyOnHill);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #2: Defend the hill if we own it and it's not contested.
        if (koth.getHill().controllingTeam() == self.getTeam() && !koth.getHill().contested()) {
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Patrol the hill
            self.setObjectiveTargetPoint(koth.getHill().position());

            // If an enemy gets close while we're defending, attack them.
            Player closestEnemy = findClosestEnemy(self, allPlayers);
            // Use an expanded guard radius around the hill's center
            if (closestEnemy != null && closestEnemy.getCenter().distanceSquared(koth.getHill().position()) < 500 * 500) {
                self.setCurrentTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }

        // If we don't own the hill or it's contested, help capture it like a Balanced AI.
        runBalancedLogic(self, allPlayers, koth);
    }

    /**
     * Objective Hound: Aggressively pursues capturing the hill.
     */
    private void prioritizeObjective(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // Priority #1: Hunt enemies on the hill with relentless determination!
        Player enemyOnHill = findClosestEnemyOnHill(self, allPlayers, koth);
        if (enemyOnHill != null) {
            self.setCurrentTarget(enemyOnHill);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #2: Capturing the hill if it's not ours or contested.
        if (koth.getHill().controllingTeam() != self.getTeam() || koth.getHill().contested()) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(koth.getHill().position());
            return;
        }

        // Priority #3: Only fight enemies that are very close and threatening the hill
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null && isInRange(self, closestEnemy, 300)) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #4: Patrol the hill to maintain control
        self.setCurrentState(AIPlayer.AIState.WANDERING);
        self.setObjectiveTargetPoint(koth.getHill().position());
    }

    /**
     * A specialized version of findClosestEnemy that only considers enemies
     * within the hill's radius. This makes the Objective Hound ignore distant fights.
     */
    private Player findClosestEnemyOnHill(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        Player closestEnemy = null;
        double minDistanceSq = Double.MAX_VALUE;
        double hillRadiusSq = koth.getHill().radius() * koth.getHill().radius();

        for (Player other : allPlayers) {
            // Skip self, teammates, or dead players
            if (Objects.equals(other.getId(), self.getId()) || other.getTeam() == self.getTeam() || other.isDead()) {
                continue;
            }

            // Check if the enemy is on the hill
            if (other.getCenter().distanceSquared(koth.getHill().position()) > hillRadiusSq) {
                continue;
            }

            double distanceSq = self.getCenter().distanceSquared(other.getCenter());
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestEnemy = other;
            }
        }
        return closestEnemy;
    }
}