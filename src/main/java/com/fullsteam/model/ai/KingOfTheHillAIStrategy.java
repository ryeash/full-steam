package com.fullsteam.model.ai;

import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;

import java.util.Collection;

public class KingOfTheHillAIStrategy implements IAIStrategy {

    // Use a static final instance for a safe fallback.
    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        if (!(gameInfo instanceof KingOfTheHillInfo koth)) {
            // Fallback safely instead of crashing the server if the game mode is wrong.
            FALLBACK_STRATEGY.updateAIState(self, allPlayers, gameInfo);
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
        // Priority 1: An enemy is nearby. ATTACK!
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setNewTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: The hill is not ours. CAPTURE!
        if (koth.getHill().controllingTeam() != self.getTeam()) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(koth.getHill().position());
        } else {
            // Priority 3: The hill is ours. DEFEND! (Patrol the area)
            self.setCurrentState(AIPlayer.AIState.WANDERING);
            self.setObjectiveTargetPoint(koth.getHill().position());
        }
    }

    /**
     * Warrior: Always seeks combat first, plays the objective second.
     */
    private void prioritizeCombat(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // The Warrior's #1 priority is always to fight.
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setNewTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }
        // If no one is around to fight, it will play the objective as a secondary goal.
        runBalancedLogic(self, allPlayers, koth);
    }

    /**
     * Guardian: Prioritizes holding the hill and defending it.
     */
    private void prioritizeDefense(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // The Guardian's #1 priority is defending the hill if we own it.
        if (koth.getHill().controllingTeam() == self.getTeam()) {
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Patrol the hill
            self.setObjectiveTargetPoint(koth.getHill().position());

            // If an enemy gets close while we're defending, attack them.
            Player closestEnemy = findClosestEnemy(self, allPlayers);
            // Use a guard radius around the hill's center
            if (closestEnemy != null && closestEnemy.getCenter().distanceSq(koth.getHill().position()) < 400 * 400) {
                self.setNewTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }

        // If we don't own the hill, the Guardian will help capture it like a Balanced AI.
        runBalancedLogic(self, allPlayers, koth);
    }

    /**
     * Objective Hound: Aggressively pursues capturing the hill.
     */
    private void prioritizeObjective(AIPlayer self, Collection<Player> allPlayers, KingOfTheHillInfo koth) {
        // The Objective Hound's #1 priority is capturing the hill if it's not ours.
        if (koth.getHill().controllingTeam() != self.getTeam()) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(koth.getHill().position());
            return;
        }

        // If we own the hill, it will defend it, but it will only fight enemies
        // who are a direct threat to the objective.
        Player closestEnemyOnHill = findClosestEnemyOnHill(self, allPlayers, koth);
        if (closestEnemyOnHill != null) {
            self.setNewTarget(closestEnemyOnHill);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // If no enemies are on the hill, just patrol it.
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
            if (other.getId().equals(self.getId()) || other.getTeam() == self.getTeam() || other.isDead()) {
                continue;
            }

            // Check if the enemy is on the hill
            if (other.getCenter().distanceSq(koth.getHill().position()) > hillRadiusSq) {
                continue;
            }

            double distanceSq = self.getCenter().distanceSq(other.getCenter());
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestEnemy = other;
            }
        }
        return closestEnemy;
    }
}