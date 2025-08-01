package com.fullsteam.model.ai;

import com.fullsteam.model.Flag;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;

public class CaptureTheFlagAIStrategy implements IAIStrategy {

    // Use a static final instance to avoid creating a new object on every fallback.
    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        if (!(gameInfo instanceof CaptureTheFlagInfo ctf)) {
            FALLBACK_STRATEGY.updateAIState(self, allPlayers, gameInfo); // Fallback for safety
            return;
        }

        // --- Pre-computation: Get key state information once ---
        Flag myFlag = self.getTeam() == 1 ? ctf.getTeam1Flag() : ctf.getTeam2Flag();
        Flag enemyFlag = self.getTeam() == 1 ? ctf.getTeam2Flag() : ctf.getTeam1Flag();
        boolean amICarryingFlag = self.getId().equals(enemyFlag.carrierId());

        // --- Archetype-based Decision Making ---
        // The AI's "personality" determines its priorities.
        switch (self.archetype()) {
            case WARRIOR:
                prioritizeCombat(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
                break;
            case GUARDIAN:
                prioritizeDefense(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
                break;
            case OBJECTIVE_HOUND:
                prioritizeObjective(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
                break;
            case BALANCED:
            default:
                runBalancedLogic(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
                break;
        }
    }

    /**
     * A well-rounded strategy that serves as the default behavior.
     */
    private void runBalancedLogic(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // Priority 1: An enemy is nearby. ATTACK!
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: I have the flag. SCORE!
        if (amICarryingFlag) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.basePosition());
            return;
        }

        // Priority 3: Our flag is dropped. RECOVER!
        if (myFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.position());
            return;
        }

        // Priority 4: Enemy flag is available. STEAL!
        if (enemyFlag.state() == Flag.FlagState.AT_BASE) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(enemyFlag.basePosition());
            return;
        }
        if (enemyFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(enemyFlag.position());
            return;
        }

        // Priority 5: All is well, wander and defend our flag's general area.
        self.setCurrentState(AIPlayer.AIState.WANDERING);
        self.setObjectiveTargetPoint(myFlag.basePosition());
    }

    /**
     * Warrior: Always seeks combat first, plays the objective second.
     */
    private void prioritizeCombat(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // The Warrior's #1 priority is always to fight.
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }
        // If no one is around to fight, it will play the objective as a secondary goal.
        runBalancedLogic(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
    }

    /**
     * Guardian: Prioritizes flag defense and recovery above all else.
     */
    private void prioritizeDefense(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // The Guardian's #1 priority is recovering our flag.
        if (myFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.position());
            return;
        }

        // Priority #2 is defending our flag at the base.
        if (myFlag.state() == Flag.FlagState.AT_BASE) {
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Patrol the base
            self.setObjectiveTargetPoint(myFlag.basePosition());
            // If an enemy gets close while we're defending, attack them.
            Player closestEnemy = findClosestEnemy(self, allPlayers);
            if (closestEnemy != null && isInRange(self, closestEnemy, 300)) { // Defend a 300-unit radius
                self.setCurrentTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }

        // If our flag is safe and not at base (i.e., a teammate has it), play normally.
        runBalancedLogic(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
    }

    /**
     * Objective Hound: Aggressively pursues the enemy flag, ignoring most combat.
     */
    private void prioritizeObjective(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // The Objective Hound's #1 priority is scoring if it has the flag.
        if (amICarryingFlag) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.basePosition());
            return;
        }

        // Priority #2 is grabbing the enemy flag if it's available.
        if (enemyFlag.state() == Flag.FlagState.AT_BASE) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(enemyFlag.basePosition());
            return;
        }
        if (enemyFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(enemyFlag.position());
            return;
        }

        // If it can't go for the enemy flag, it will fight anyone directly in its way.
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Otherwise, it will help with defense as a last resort.
        runBalancedLogic(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
    }
}