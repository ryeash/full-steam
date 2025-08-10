package com.fullsteam.model.ai;

import com.fullsteam.Config;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Oddball;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.OddballInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public class OddballAIStrategy implements IAIStrategy {

    // Use a static final instance to avoid creating a new object on every fallback.
    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        Collection<Player> allPlayers = gameState.players();
        GameInfo gameInfo = gameState.info();

        if (!(gameInfo instanceof OddballInfo oddballInfo)) {
            FALLBACK_STRATEGY.updateAIState(self, gameState); // Fallback
            return;
        }

        // --- Pre-computation: Get key state information once ---
        Oddball oddball = oddballInfo.getOddball();
        Player closestEnemy = findClosestEnemy(self, allPlayers);
        boolean isCarrier = Objects.equals(self.getId(), oddball.carrierId());

        // Find the carrier Player object once to get their team and position efficiently.
        Optional<Player> carrierOpt = allPlayers.stream()
                .filter(p -> Objects.equals(p.getId(), oddball.carrierId()))
                .findFirst();

        // --- Archetype-based Decision Making ---
        // The AI's "personality" determines its priorities.
        switch (self.archetype()) {
            case WARRIOR:
                prioritizeCombat(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
                break;
            case GUARDIAN:
                prioritizeDefense(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
                break;
            case OBJECTIVE_HOUND:
                prioritizeObjective(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
                break;
            case BALANCED:
            default:
                // The original, balanced logic serves as the default.
                runBalancedLogic(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
                break;
        }
    }

    /**
     * Finds the closest living teammate to the AI player.
     *
     * @param self       The AI player.
     * @param allPlayers A collection of all players in the game.
     * @return The closest teammate, or null if none are found.
     */
    private Player findClosestTeammate(AIPlayer self, Collection<Player> allPlayers) {
        return allPlayers.stream()
                .filter(p -> p.getTeam() == self.getTeam() && !Objects.equals(p.getId(), self.getId()))
                .min(Comparator.comparingDouble(p -> p.position().distanceSquared(self.position())))
                .orElse(null);
    }

    /**
     * Determines a safe point deep within a team's territory.
     * Used by the ball carrier to decide where to run if they are alone.
     */
    private Vector2D getSafePointForTeam(int team) {
        if (team == 1) {
            // Team 1's safe zone is on the left side of the map.
            return new Vector2D(Config.GAME_WIDTH * 0.25, Config.GAME_HEIGHT / 2.0);
        } else {
            // Team 2's safe zone is on the right side of the map.
            return new Vector2D(Config.GAME_WIDTH * 0.75, Config.GAME_HEIGHT / 2.0);
        }
    }

    // --- Balanced Logic (Original Behavior) ---
    private void runBalancedLogic(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        // Priority 1: I am the ball carrier. SURVIVE AND SCORE!
        if (isCarrier) {
            // Always get the safe point on our side
            Vector2D safePoint = getSafePointForTeam(self.getTeam());

            // Find the closest teammate to use as cover
            Player closestTeammate = findClosestTeammate(self, allPlayers);

            // If we have a nearby teammate in a good defensive position (closer to our safe zone), use them as cover
            if (closestTeammate != null) {
                double teammateDistToSafeSq = closestTeammate.position().distanceSquared(safePoint);
                double myDistToSafeSq = self.position().distanceSquared(safePoint);

                if (teammateDistToSafeSq < myDistToSafeSq) {
                    self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                    self.setObjectiveTargetPoint(closestTeammate.position());
                    return;
                }
            }

            // No good teammate cover, focus on getting to safety
            double distanceToSafePointSq = self.position().distanceSquared(safePoint);

            // If we're at our safe point, do evasive wandering
            if (distanceToSafePointSq < 150 * 150) { // Reduced radius to stay closer to safe point
                self.setCurrentState(AIPlayer.AIState.WANDERING);
                self.setObjectiveTargetPoint(safePoint); // Keep the safe point as reference for wandering
            } else {
                // Run towards safety!
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(safePoint);
            }

            // If there's an enemy too close, consider them a threat but keep moving
            if (closestEnemy != null && isInRange(self, closestEnemy, 200)) {
                self.setCurrentTarget(closestEnemy); // Track them for shooting while retreating
            }
            return;
        }

        // Priority 2: HUNT enemy ball carrier with extreme prejudice!
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() != self.getTeam()) {
            Player enemyCarrier = carrierOpt.get();
            self.setCurrentTarget(enemyCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 3: The ball is loose. GET IT!
        if (oddball.state() == Oddball.OddballState.ON_SPAWN || oddball.state() == Oddball.OddballState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(oddball.position());
            return;
        }

        // Priority 4: An enemy is nearby and no objectives available. ATTACK!
        if (closestEnemy != null && isInRange(self, closestEnemy, 400)) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 5: PROTECT the friendly carrier
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() == self.getTeam()) {
            Player friendlyCarrier = carrierOpt.get();
            self.setCurrentState(AIPlayer.AIState.WANDERING);
            self.setObjectiveTargetPoint(friendlyCarrier.position());
            return;
        }

        self.setCurrentState(AIPlayer.AIState.WANDERING);
    }

    // --- Archetype-Specific Logic ---

    private void prioritizeCombat(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        if (isCarrier) {
            runBalancedLogic(self, closestEnemy, oddball, isCarrier, Optional.empty(), allPlayers);
        }

        // Even warriors prioritize hunting the enemy ball carrier
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() != self.getTeam()) {
            Player enemyCarrier = carrierOpt.get();
            self.setCurrentTarget(enemyCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // The Warrior's #2 priority is always to fight anyone else.
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }
        // If no one is around to fight, it will play the objective as a tertiary goal.
        runBalancedLogic(self, null, oddball, isCarrier, carrierOpt, allPlayers);
    }

    private void prioritizeDefense(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        if (isCarrier) {
            runBalancedLogic(self, closestEnemy, oddball, isCarrier, Optional.empty(), allPlayers);
        }

        // Priority #1: Hunt enemy ball carrier with extreme prejudice!
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() != self.getTeam()) {
            Player enemyCarrier = carrierOpt.get();
            self.setCurrentTarget(enemyCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #2: Protecting the friendly carrier.
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() == self.getTeam()) {
            Player friendlyCarrier = carrierOpt.get();
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Default to wandering near the carrier
            self.setObjectiveTargetPoint(friendlyCarrier.position());

            // If an enemy gets close to the carrier, the Guardian will engage.
            if (closestEnemy != null && closestEnemy.position().distanceSquared(friendlyCarrier.position()) < 400 * 400) { // 400 unit guard radius
                self.setCurrentTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }
        // If there's no carrier to protect, it will play normally.
        runBalancedLogic(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
    }

    private void prioritizeObjective(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        if (isCarrier) {
            runBalancedLogic(self, closestEnemy, oddball, isCarrier, Optional.empty(), allPlayers);
        }

        // Priority #1: Hunt enemy ball carrier with relentless determination!
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() != self.getTeam()) {
            Player enemyCarrier = carrierOpt.get();
            self.setCurrentTarget(enemyCarrier);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #2: Getting the ball if it's loose.
        if (oddball.state() == Oddball.OddballState.ON_SPAWN || oddball.state() == Oddball.OddballState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(oddball.position());
            return;
        }

        // Priority #3: Only fight enemies that are blocking objective paths or very close
        if (closestEnemy != null && isInRange(self, closestEnemy, 250)) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority #4: Position near ball spawn for opportunities
        if (oddball.state() == Oddball.OddballState.CARRIED) {
            self.setCurrentState(AIPlayer.AIState.WANDERING);
            self.setObjectiveTargetPoint(oddball.position()); // Move toward where the ball will likely drop
            return;
        }

        // Default fallback
        runBalancedLogic(self, null, oddball, isCarrier, carrierOpt, allPlayers);
    }
}
