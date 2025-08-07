package com.fullsteam.model.ai;

import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;

import java.util.Collection;
import java.util.Comparator;

/**
 * A very simple AI strategy for zombies. Their only goal is to find the
 * closest living human player and move directly towards them.
 */
public class ZombieAIStrategy implements IAIStrategy {

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();

        // Find the closest living human player
        allPlayers.stream()
                .filter(p -> p.getTeam() == 1 && !p.isDead())
                .min(Comparator.comparingDouble(p -> p.getCenter().distanceSquared(self.getCenter())))
                .ifPresentOrElse(
                        target -> {
                            // A human target has been found. Decide whether to attack or chase.
                            double attackRange = self.getWeapon().getBulletRange();

                            if (isInRange(self, target, attackRange * 0.9)) {
                                // If the target is within attack range, switch to attack mode.
                                self.setCurrentTarget(target);
                                self.setCurrentState(AIPlayer.AIState.ATTACKING);
                            } else {
                                // If the target is out of range, chase them.
                                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                                self.setObjectiveTargetPoint(target.getCenter());
                            }
                        },
                        () -> {
                            // No humans are left, or none are visible. Wander aimlessly.
                            self.setCurrentState(AIPlayer.AIState.WANDERING);
                        }
                );
    }
}