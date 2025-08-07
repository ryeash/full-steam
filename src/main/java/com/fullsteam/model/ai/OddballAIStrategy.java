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
                .min(Comparator.comparingDouble(p -> p.getCenter().distanceSquared(self.getCenter())))
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
        // Priority 1: I am the ball carrier. RUN FOR COVER!
        if (isCarrier) {
            // Find the closest teammate to run to for protection.
            Player closestTeammate = findClosestTeammate(self, allPlayers);

            if (closestTeammate != null) {
                // If we have a teammate, run towards them for protection.
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(closestTeammate.getCenter());
            } else {
                // If we are alone, fall back to the original behavior of running to a safe point.
                Vector2D safePoint = getSafePointForTeam(self.getTeam());
                double distanceToSafePointSq = self.getCenter().distanceSquared(safePoint);

                // If we are already near our safe point, just wander around it to evade.
                if (distanceToSafePointSq < 200 * 200) { // 200 unit radius
                    self.setCurrentState(AIPlayer.AIState.WANDERING);
                } else {
                    // Otherwise, run towards the safe point.
                    self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                }
                self.setObjectiveTargetPoint(safePoint);
            }
            return;
        }

        // Priority 2: An enemy is nearby. ATTACK!
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 3: The ball is loose. GET IT!
        if (oddball.state() == Oddball.OddballState.ON_SPAWN || oddball.state() == Oddball.OddballState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(oddball.position());
            return;
        }

        // Priority 4 & 5: A teammate or enemy has the ball.
        if (carrierOpt.isPresent()) {
            Player carrier = carrierOpt.get();
            if (carrier.getTeam() != self.getTeam()) {
                // HUNT the enemy carrier
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(carrier.getCenter());
            } else {
                // PROTECT the friendly carrier
                self.setCurrentState(AIPlayer.AIState.WANDERING);
                self.setObjectiveTargetPoint(carrier.getCenter());
            }
            return;
        }

        self.setCurrentState(AIPlayer.AIState.WANDERING);
    }

    // --- Archetype-Specific Logic ---

    private void prioritizeCombat(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        // The Warrior's #1 priority is always to fight.
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }
        // If no one is around to fight, it will play the objective as a secondary goal.
        runBalancedLogic(self, null, oddball, isCarrier, carrierOpt, allPlayers);
    }

    private void prioritizeDefense(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        // The Guardian's #1 priority is protecting the friendly carrier.
        if (carrierOpt.isPresent() && carrierOpt.get().getTeam() == self.getTeam()) {
            Player friendlyCarrier = carrierOpt.get();
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Default to wandering near the carrier
            self.setObjectiveTargetPoint(friendlyCarrier.getCenter());

            // If an enemy gets close to the carrier, the Guardian will engage.
            if (closestEnemy != null && closestEnemy.getCenter().distanceSquared(friendlyCarrier.getCenter()) < 400 * 400) { // 400 unit guard radius
                self.setCurrentTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }
        // If there's no friendly carrier to protect, it will play normally.
        runBalancedLogic(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
    }

    private void prioritizeObjective(AIPlayer self, Player closestEnemy, Oddball oddball, boolean isCarrier, Optional<Player> carrierOpt, Collection<Player> allPlayers) {
        // The Objective Hound's #1 priority is getting the ball if it's loose.
        if (oddball.state() == Oddball.OddballState.ON_SPAWN || oddball.state() == Oddball.OddballState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(oddball.position());
            return;
        }
        // If it can't get the ball, it will play normally.
        runBalancedLogic(self, closestEnemy, oddball, isCarrier, carrierOpt, allPlayers);
    }
}
