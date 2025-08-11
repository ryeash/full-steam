package com.fullsteam.model.ai;

import com.fullsteam.model.Flag;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;
import java.util.Objects;

public class CaptureTheFlagAIStrategy implements IAIStrategy {

    // Use a static final instance to avoid creating a new object on every fallback.
    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();
        GameInfo gameInfo = gameState.info();

        if (!(gameInfo instanceof CaptureTheFlagInfo ctf)) {
            FALLBACK_STRATEGY.updateAIState(self, gameState); // Fallback for safety
            return;
        }

        // --- Pre-computation: Get key state information once ---
        Flag myFlag = self.getTeam() == 1 ? ctf.getTeam1Flag() : ctf.getTeam2Flag();
        Flag enemyFlag = self.getTeam() == 1 ? ctf.getTeam2Flag() : ctf.getTeam1Flag();
        boolean amICarryingFlag = Objects.equals(self.getId(), enemyFlag.carrierId());

        // --- Universal Priority #1: Handle flag carrier logic ---
        if (handleFlagCarrierLogic(self, allPlayers, myFlag, amICarryingFlag)) {
            return; // Logic is handled, no further action needed.
        }

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
     * Handles the AI's behavior when it is carrying the enemy flag.
     * The primary goal is to return to base as quickly as possible to score.
     * @return true if the AI is carrying the flag, false otherwise.
     */
    private boolean handleFlagCarrierLogic(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, boolean amICarryingFlag) {
        if (!amICarryingFlag) {
            return false;
        }

        // Priority #1 for a flag carrier is to get back to base and score.
        // It will ignore all other distractions and sprint home.
        self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
        self.setObjectiveTargetPoint(myFlag.basePosition());

        return true; // Logic is handled, no further action needed.
    }


    /**
     * A well-rounded strategy that serves as the default behavior.
     */
    private void runBalancedLogic(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // Priority 1: Hunt enemy flag carrier with extreme prejudice!
        Player enemyFlagCarrier = findEnemyFlagCarrier(self, allPlayers, enemyFlag);
        if (enemyFlagCarrier != null) {
            self.setCurrentTarget(enemyFlagCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: Our flag is dropped. RECOVER!
        if (myFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.position());
            return;
        }

        // Priority 3: Enemy flag is available. STEAL!
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

        // Priority 4: An enemy is nearby and no objectives are available. ATTACK!
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null && isInRange(self, closestEnemy, 400)) { // Only attack if reasonably close
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 5: All is well, wander and defend our flag's general area.
        self.setCurrentState(AIPlayer.AIState.WANDERING);
        self.setObjectiveTargetPoint(myFlag.basePosition());
    }

    /**
     * Warrior: Prioritizes hunting flag carriers, then general combat, then objectives.
     */
    private void prioritizeCombat(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // Even warriors prioritize hunting enemy flag carriers
        Player enemyFlagCarrier = findEnemyFlagCarrier(self, allPlayers, enemyFlag);
        if (enemyFlagCarrier != null) {
            self.setCurrentTarget(enemyFlagCarrier);
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
        runBalancedLogic(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
    }

    /**
     * Guardian: Prioritizes flag defense and recovery above all else.
     */
    private void prioritizeDefense(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // Priority #1: Hunt enemy flag carrier with extreme prejudice!
        Player enemyFlagCarrier = findEnemyFlagCarrier(self, allPlayers, enemyFlag);
        if (enemyFlagCarrier != null) {
            self.setCurrentTarget(enemyFlagCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #2: Recovering our flag.
        if (myFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.position());
            return;
        }

        // Priority #3: Defending our flag at the base.
        if (myFlag.state() == Flag.FlagState.AT_BASE) {
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Patrol the base
            self.setObjectiveTargetPoint(myFlag.basePosition());
            // If an enemy gets close while we're defending, attack them.
            Player closestEnemy = findClosestEnemy(self, allPlayers);
            if (closestEnemy != null && isInRange(self, closestEnemy, 400)) { // Defend a 400-unit radius
                self.setCurrentTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }

        // If our flag is safe and not at base (i.e., a teammate has it), play normally.
        runBalancedLogic(self, allPlayers, myFlag, enemyFlag, amICarryingFlag);
    }

    /**
     * Objective Hound: Aggressively pursues the enemy flag, prioritizing flag-related tasks.
     */
    private void prioritizeObjective(AIPlayer self, Collection<Player> allPlayers, Flag myFlag, Flag enemyFlag, boolean amICarryingFlag) {
        // Priority 1: Hunt enemy flag carrier with relentless determination!
        Player enemyFlagCarrier = findEnemyFlagCarrier(self, allPlayers, enemyFlag);
        if (enemyFlagCarrier != null) {
            self.setCurrentTarget(enemyFlagCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: Our flag is dropped - secure it first!
        if (myFlag.state() == Flag.FlagState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(myFlag.position());
            return;
        }

        // Priority 3: Grabbing the enemy flag if it's available.
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

        // Priority 4: Only fight enemies that are blocking objective paths or very close
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null && isInRange(self, closestEnemy, 250)) { // Only engage if very close
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 5: Move toward enemy base to be ready for flag opportunities
        self.setCurrentState(AIPlayer.AIState.WANDERING);
        self.setObjectiveTargetPoint(enemyFlag.basePosition());
    }

    /**
     * Finds the enemy player who is currently carrying our flag.
     * This is the highest priority target for all AI archetypes.
     */
    private Player findEnemyFlagCarrier(AIPlayer self, Collection<Player> allPlayers, Flag enemyFlag) {
        if (enemyFlag.state() != Flag.FlagState.CARRIED) {
            return null; // No one is carrying the flag
        }

        Long carrierId = enemyFlag.carrierId();
        if (carrierId == null) {
            return null; // No carrier ID available
        }

        return allPlayers.stream()
                .filter(player -> !player.isDead())
                .filter(player -> player.getTeam() != self.getTeam()) // Only enemy players
                .filter(player -> Objects.equals(player.getId(), carrierId))
                .findFirst()
                .orElse(null);
    }
}
