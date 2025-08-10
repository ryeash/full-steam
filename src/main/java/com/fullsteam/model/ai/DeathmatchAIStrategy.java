package com.fullsteam.model.ai;

import com.fullsteam.Config;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;

import java.util.Collection;

public class DeathmatchAIStrategy implements IAIStrategy {
    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();

        // --- Pre-computation: Get key state information once ---
        Player closestEnemy = findClosestEnemy(self, allPlayers);

        // --- Archetype-based Decision Making ---
        // The AI's "personality" determines its priorities, even in a simple deathmatch.
        switch (self.archetype()) {
            case WARRIOR:
            case OBJECTIVE_HOUND: // In Deathmatch, the objective *is* combat.
                // These archetypes are purely aggressive. They will always seek a fight.
                prioritizeCombat(self, closestEnemy);
                break;

            case GUARDIAN:
                // The Guardian is more cautious and values self-preservation.
                prioritizeDefense(self, closestEnemy);
                break;

            case BALANCED:
            default:
                // The default, balanced behavior.
                runBalancedLogic(self, closestEnemy);
                break;
        }
    }

    /**
     * The default behavior: find the nearest enemy and attack.
     * Uses weapon range to determine optimal engagement distance.
     */
    private void runBalancedLogic(AIPlayer self, Player closestEnemy) {
        if (closestEnemy != null) {
            double distanceSq = self.position().distanceSquared(closestEnemy.position());
            double weaponRange = self.getWeapon().getBulletRange();
            double optimalRangeSq = (weaponRange * 0.6) * (weaponRange * 0.6); // Try to get to 60% of max range
            
            self.setCurrentTarget(closestEnemy);
            
            // If we're too far away, use CAPTURING_OBJECTIVE to aggressively close distance
            if (distanceSq > optimalRangeSq) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(closestEnemy.position());
            } else {
                // Within good range, switch to ATTACKING which includes strafing behavior
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
        } else {
            // No enemies in sight, so wander to find one
            self.setCurrentState(AIPlayer.AIState.WANDERING);
        }
    }

    /**
     * Warrior/Objective Hound: Pure, relentless aggression.
     * More aggressive than balanced, using a closer optimal range.
     */
    private void prioritizeCombat(AIPlayer self, Player closestEnemy) {
        if (closestEnemy != null) {
            double distanceSq = self.position().distanceSquared(closestEnemy.position());
            double weaponRange = self.getWeapon().getBulletRange();
            // Warriors want to get much closer - to 40% of max range
            double optimalRangeSq = (weaponRange * 0.4) * (weaponRange * 0.4);
            
            self.setCurrentTarget(closestEnemy);
            
            // More aggressive range management
            if (distanceSq > optimalRangeSq) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(closestEnemy.position());
            } else {
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
        } else {
            // More aggressive wandering - use CAPTURING_OBJECTIVE to move faster
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            // Pick a point in the center of the map for hunting
            self.setObjectiveTargetPoint(new Vector2D(Config.GAME_WIDTH / 2.0, Config.GAME_HEIGHT / 2.0));
        }
    }

    /**
     * Guardian: Engages in combat but will retreat if badly wounded.
     * Uses weapon range for optimal positioning.
     */
    private void prioritizeDefense(AIPlayer self, Player closestEnemy) {
        if (closestEnemy == null) {
            // No enemies, patrol center area
            self.setCurrentState(AIPlayer.AIState.WANDERING);
            return;
        }

        double distanceSq = self.position().distanceSquared(closestEnemy.position());
        double weaponRange = self.getWeapon().getBulletRange();
        double healthRatio = self.getHp() / (double) self.getMaxHp();
        
        // Adjust optimal range based on health
        double rangeMultiplier = healthRatio < 0.4 ? 0.8 : // Stay far when wounded
                                healthRatio < 0.7 ? 0.7 :   // Medium range when damaged
                                0.6;                        // Normal range when healthy
        
        double optimalRangeSq = (weaponRange * rangeMultiplier) * (weaponRange * rangeMultiplier);
        
        self.setCurrentTarget(closestEnemy);
        
        // Too close and wounded - retreat!
        if (distanceSq < optimalRangeSq && healthRatio < 0.4) {
            self.setCurrentState(AIPlayer.AIState.FLEEING);
            self.setObjectiveTargetPoint(closestEnemy.position());
        }
        // Too far - close in carefully
        else if (distanceSq > optimalRangeSq) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(closestEnemy.position());
        }
        // At good range - engage
        else {
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
        }
    }
}