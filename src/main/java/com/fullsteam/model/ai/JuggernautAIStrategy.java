package com.fullsteam.model.ai;

import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.JuggernautInfo;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class JuggernautAIStrategy implements IAIStrategy {

    private final boolean attacker;

    public JuggernautAIStrategy() {
        attacker = ThreadLocalRandom.current().nextBoolean();
    }

    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        if (gameInfo instanceof JuggernautInfo j) {

            Player closestEnemy = findClosestEnemy(self, allPlayers);

            // --- If an enemy is right here, attack them immediately ---
            if (closestEnemy != null) {
                self.setNewTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
                return;
            }

            // --- No immediate threats, focus on the Juggernaut objective ---
            self.setCurrentTarget(null);
            self.setObjectiveTargetPoint(null);

            Optional<Player> myJuggernaut = findJuggernautForTeam(allPlayers, self.getTeam(), j);
            Optional<Player> enemyJuggernaut = findJuggernautForTeam(allPlayers, (self.getTeam() % 2) + 1, j);

            // If I am NOT the Juggernaut, I must decide to attack or defend.
            if (myJuggernaut.isPresent() && enemyJuggernaut.isPresent()) {
                if (Objects.equals(self.getId(), myJuggernaut.get().getId())) {
                    // If I AM the Juggernaut, my goal is to hunt the enemy Juggernaut.
                    self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                    self.setObjectiveTargetPoint(enemyJuggernaut.get().getCenter());
                } else if (attacker) {
                    // attack the opposing juggernaut
                    self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                    self.setObjectiveTargetPoint(enemyJuggernaut.get().getCenter());
                } else {
                    // defend my juggernaut
                    self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                    self.setObjectiveTargetPoint(myJuggernaut.get().getCenter());
                }
            } else {
                // If a Juggernaut is missing (e.g., round starting), just wander.
                self.setCurrentState(AIPlayer.AIState.WANDERING);
            }
        } else {
            throw new IllegalStateException("wrong AI strategy");
        }
    }

    private Optional<Player> findJuggernautForTeam(Collection<Player> players, int team, JuggernautInfo j) {
        String juggernautId = team == 1 ? j.getTeam1Juggernaut() : j.getTeam2Juggernaut();
        if (juggernautId == null) {
            return Optional.empty();
        }
        return players.stream()
                .filter(p -> p.getId().equals(juggernautId))
                .findFirst();
    }
}