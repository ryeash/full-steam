package com.fullsteam.model.ai;

import com.fullsteam.Config;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.gamemodes.EscortGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class EscortAIStrategy implements IAIStrategy {

    private static final DeathmatchAIStrategy FALLBACK_STRATEGY = new DeathmatchAIStrategy();

    @Override
    public void updateAIState(AIPlayer self, GameState gameState) {
        GameInfo gameInfo = gameState.info();
        if (!(gameInfo instanceof EscortGameInfo escortInfo)) {
            FALLBACK_STRATEGY.updateAIState(self, gameState);
            return;
        }

        // --- Pre-computation ---
        Collection<Player> allPlayers = gameState.players();
        Obstacle payload = escortInfo.getObstacle();
        Vector2D payloadCenter = getPayloadCenter(payload);
        Player closestEnemy = findClosestEnemy(self, allPlayers);

        // Determine which teams are currently pushing the payload
        double proximitySq = Config.ESCORT_PLAYER_PROXIMITY * Config.ESCORT_PLAYER_PROXIMITY;
        Set<Integer> teamsNearPayload = allPlayers.stream()
                .filter(p -> !p.isDead())
                .filter(p -> p.getCenter().distanceSquared(payloadCenter) < proximitySq)
                .map(Player::getTeam)
                .collect(Collectors.toSet());

        boolean isOurTeamPushing = teamsNearPayload.contains(self.getTeam()) && teamsNearPayload.size() == 1;

        // --- Archetype-based Decision Making ---
        switch (self.archetype()) {
            case WARRIOR:
                prioritizeCombat(self, closestEnemy, payloadCenter, isOurTeamPushing);
                break;
            case GUARDIAN:
                prioritizeDefense(self, allPlayers, closestEnemy, payload, payloadCenter, isOurTeamPushing);
                break;
            case OBJECTIVE_HOUND:
                prioritizeObjective(self, allPlayers, payload, payloadCenter);
                break;
            case BALANCED:
            default:
                runBalancedLogic(self, closestEnemy, payloadCenter, isOurTeamPushing);
                break;
        }
    }

    /**
     * Default, well-rounded behavior.
     */
    private void runBalancedLogic(AIPlayer self, Player closestEnemy, Vector2D payloadCenter, boolean isOurTeamPushing) {
        // Priority 1: An enemy is very close. ATTACK!
        if (closestEnemy != null && self.getCenter().distanceSquared(closestEnemy.getCenter()) < 250 * 250) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // Priority 2: Our team is pushing the payload. DEFEND IT!
        if (isOurTeamPushing) {
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Patrol near the payload
            self.setObjectiveTargetPoint(payloadCenter);
        } else {
            // Priority 3: The payload is stalled or pushed by the enemy. GET TO THE PAYLOAD!
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(payloadCenter);
        }
    }

    /**
     * Warrior: Fights first, asks questions later.
     */
    private void prioritizeCombat(AIPlayer self, Player closestEnemy, Vector2D payloadCenter, boolean isOurTeamPushing) {
        // The Warrior's #1 priority is always to fight.
        if (closestEnemy != null) {
            self.setCurrentTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }
        // If no one is around to fight, it will play the objective.
        runBalancedLogic(self, null, payloadCenter, isOurTeamPushing);
    }

    /**
     * Guardian: Prioritizes defending a successful push.
     */
    private void prioritizeDefense(AIPlayer self, Collection<Player> allPlayers, Player closestEnemy, Obstacle payload, Vector2D payloadCenter, boolean isOurTeamPushing) {
        // The Guardian's #1 priority is defending the payload if our team is pushing it.
        if (isOurTeamPushing) {
            self.setCurrentState(AIPlayer.AIState.WANDERING); // Patrol the payload
            self.setObjectiveTargetPoint(payloadCenter);

            // If an enemy gets close to the payload while we're defending, attack them.
            Player enemyNearPayload = findClosestEnemyNearPayload(self, allPlayers, payload, 400.0);
            if (enemyNearPayload != null) {
                self.setCurrentTarget(enemyNearPayload);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
            }
            return;
        }

        // If we aren't pushing, the Guardian will help capture it like a Balanced AI.
        runBalancedLogic(self, closestEnemy, payloadCenter, false);
    }

    /**
     * Objective Hound: Aggressively pursues the payload.
     */
    private void prioritizeObjective(AIPlayer self, Collection<Player> allPlayers, Obstacle payload, Vector2D payloadCenter) {
        // The Objective Hound's #1 priority is getting to the payload.
        self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
        self.setObjectiveTargetPoint(payloadCenter);

        // It will only fight enemies who are a direct threat to the objective.
        Player closestEnemyNearPayload = findClosestEnemyNearPayload(self, allPlayers, payload, Config.ESCORT_PLAYER_PROXIMITY * 1.5);
        if (closestEnemyNearPayload != null) {
            self.setCurrentTarget(closestEnemyNearPayload);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
        }
    }

    /**
     * Calculates the geometric center of the payload obstacle.
     */
    private Vector2D getPayloadCenter(Obstacle payload) {
        if (payload == null || payload.vertices().isEmpty()) {
            return new Vector2D(Config.GAME_WIDTH / 2.0, Config.GAME_HEIGHT / 2.0);
        }
        return payload.vertices().stream()
                .reduce(Vector2D.ZERO, Vector2D::add)
                .multiply(1.0 / payload.vertices().size());
    }

    /**
     * A specialized version of findClosestEnemy that only considers enemies
     * within a given radius of the payload.
     */
    private Player findClosestEnemyNearPayload(AIPlayer self, Collection<Player> allPlayers, Obstacle payload, double radius) {
        Player closestEnemy = null;
        double minDistanceSq = Double.MAX_VALUE;
        double radiusSq = radius * radius;
        Vector2D payloadCenter = getPayloadCenter(payload);

        for (Player other : allPlayers) {
            // Skip self, teammates, or dead players
            if (other.getId().equals(self.getId()) || other.getTeam() == self.getTeam() || other.isDead()) {
                continue;
            }

            // Check if the enemy is near the payload
            if (other.getCenter().distanceSquared(payloadCenter) > radiusSq) {
                continue;
            }

            double distanceSq = self.getCenter().distanceSquared(other.getCenter());
            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestEnemy = other;
            }
        }
        return closestEnemy;
    }
}