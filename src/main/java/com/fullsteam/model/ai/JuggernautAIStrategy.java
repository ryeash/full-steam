package com.fullsteam.model.ai;

import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.JuggernautInfo;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class JuggernautAIStrategy implements IAIStrategy {

    // Use a static final instance to avoid creating a new object on every fallback.
    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();
        GameInfo gameInfo = gameState.info();

        if (!(gameInfo instanceof JuggernautInfo j)) {
            FALLBACK_STRATEGY.updateAIState(self, gameState); // Fallback for safety
            return;
        }

        // --- Pre-computation: Get key state information once ---
        Optional<Player> myJuggernautOpt = findJuggernautForTeam(allPlayers, self.getTeam(), j);
        Optional<Player> enemyJuggernautOpt = findJuggernautForTeam(allPlayers, (self.getTeam() % 2) + 1, j);

        // If Juggernauts aren't spawned yet (e.g., round starting), just wander.
        if (myJuggernautOpt.isEmpty() || enemyJuggernautOpt.isEmpty()) {
            self.setCurrentState(AIPlayer.AIState.WANDERING);
            return;
        }

        Player myJuggernaut = myJuggernautOpt.get();
        Player enemyJuggernaut = enemyJuggernautOpt.get();

        // --- Role-based Decision: Am I the Juggernaut? ---
        // If the AI is the Juggernaut, its archetype doesn't matter. Its role is fixed.
        if (Objects.equals(self.getId(), myJuggernaut.getId())) {
            runJuggernautLogic(self, allPlayers, enemyJuggernaut);
            return;
        }

        // --- I am NOT the Juggernaut. Decide based on my archetype. ---
        switch (self.archetype()) {
            case WARRIOR:
            case OBJECTIVE_HOUND:
                // These archetypes will focus on attacking the enemy Juggernaut.
                prioritizeOffense(self, allPlayers, enemyJuggernaut);
                break;

            case GUARDIAN:
            case BALANCED:
            default:
                // These archetypes will focus on defending our Juggernaut.
                prioritizeDefense(self, allPlayers, myJuggernaut);
                break;
        }
    }

    /**
     * The logic for an AI who IS the Juggernaut. The primary goal is to hunt the enemy.
     */
    private void runJuggernautLogic(AIPlayer self, Collection<Player> allPlayers, Player enemyJuggernaut) {
        // Priority 1: An enemy is nearby. ATTACK!
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: No immediate threats, so hunt the other Juggernaut.
        self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
        self.setObjectiveTargetPoint(enemyJuggernaut.getCenter());
    }

    /**
     * Offensive logic for Warriors and Objective Hounds.
     */
    private void prioritizeOffense(AIPlayer self, Collection<Player> allPlayers, Player enemyJuggernaut) {
        // Priority 1: Hunt the enemy Juggernaut with extreme focus!
        self.setCurrentTarget(enemyJuggernaut);
        self.setCurrentState(AIPlayer.AIState.ATTACKING);

        // Only get distracted by nearby enemies if they're very close
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null && isInRange(self, closestEnemy, 200) && 
            !Objects.equals(closestEnemy.getId(), enemyJuggernaut.getId())) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
        }
    }

    /**
     * Defensive logic for Guardians and Balanced AIs.
     */
    private void prioritizeDefense(AIPlayer self, Collection<Player> allPlayers, Player myJuggernaut) {
        // Priority 1: An enemy is near our Juggernaut. INTERCEPT!
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null && closestEnemy.getCenter().distanceSquared(myJuggernaut.getCenter()) < 400 * 400) { // 400 unit guard radius
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: No immediate threats, so patrol around our Juggernaut.
        self.setCurrentState(AIPlayer.AIState.WANDERING);
        self.setObjectiveTargetPoint(myJuggernaut.getCenter());
    }

    /**
     * Helper to find the Juggernaut player object for a given team.
     */
    private Optional<Player> findJuggernautForTeam(Collection<Player> players, int team, JuggernautInfo j) {
        Long juggernautId = team == 1 ? j.getTeam1Juggernaut() : j.getTeam2Juggernaut();
        if (juggernautId == null) {
            return Optional.empty();
        }
        return players.stream()
                .filter(p -> Objects.equals(p.getId(), juggernautId))
                .findFirst();
    }
}