package com.fullsteam.model.ai;

import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;

import java.util.Collection;

public class KingOfTheHillAIStrategy implements IAIStrategy {
    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        if (gameInfo instanceof KingOfTheHillInfo koth) {
            Player closestEnemy = findClosestEnemy(self, allPlayers);
            if (closestEnemy != null) {
                self.setNewTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
                return;
            }

            self.setCurrentTarget(null);
            self.setObjectiveTargetPoint(null);

            if (koth.getHill().controllingTeam() != self.getTeam()) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(koth.getHill().position());
            } else {
                self.setCurrentState(AIPlayer.AIState.WANDERING);
            }
        } else {
            throw new IllegalStateException("wrong AI strategy");
        }
    }
}