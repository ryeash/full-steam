package com.fullsteam.model.ai;

import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;

public class DeathmatchAIStrategy implements IAIStrategy {
    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        if (closestEnemy != null) {
            self.setNewTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
        } else {
            self.setCurrentState(AIPlayer.AIState.WANDERING);
        }
    }
}