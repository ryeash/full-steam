package com.fullsteam.model.ai;

import com.fullsteam.model.GameState;

/**
 * Simple strategy for zombies that focuses on pure pursuit
 */
class ZombieStrategy implements IAIStrategy {
    @Override
    public void updateAIState(AIPlayer ai, GameState gameState) {
        // Zombies are always in attack mode
        ai.setCurrentState(AIPlayer.AIState.ATTACKING);
    }
}
