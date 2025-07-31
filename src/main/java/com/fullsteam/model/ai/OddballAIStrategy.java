package com.fullsteam.model.ai;

import com.fullsteam.model.Oddball;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.OddballInfo;

import java.util.Collection;
import java.util.Objects;

public class OddballAIStrategy implements IAIStrategy {

    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        if (!(gameInfo instanceof OddballInfo oddballInfo)) {
            new DeathmatchAIStrategy().updateAIState(self, allPlayers, gameInfo); // Fallback
            return;
        }

        Oddball oddball = oddballInfo.getOddball();
        Player closestEnemy = findClosestEnemy(self, allPlayers);

        // Priority 1: I am the ball carrier. RUN AWAY!
        if (oddball.state() == Oddball.OddballState.CARRIED && Objects.equals(self.getId(), oddball.carrierId())) {
            self.setCurrentState(AIPlayer.AIState.FLEEING);
            if (closestEnemy != null) {
                self.setObjectiveTargetPoint(closestEnemy.getCenter());
            }
            return;
        }

        // Priority 2: An enemy is nearby. ATTACK!
        if (closestEnemy != null) {
            self.setNewTarget(closestEnemy);
            self.setCurrentState(AIPlayer.AIState.ATTACKING);
            return;
        }

        // --- No enemies nearby, focus on the ball objective ---
        self.setCurrentTarget(null);
        self.setObjectiveTargetPoint(null);

        // Priority 3: The ball is loose. GET IT!
        if (oddball.state() == Oddball.OddballState.ON_SPAWN || oddball.state() == Oddball.OddballState.DROPPED) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(oddball.position());
            return;
        }

        // Priority 4: The enemy has the ball. HUNT THEM!
        if (oddball.state() == Oddball.OddballState.CARRIED && self.getTeam() != getPlayerTeam(allPlayers, oddball.carrierId())) {
            self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
            self.setObjectiveTargetPoint(oddball.position()); // Target is the carrier's position
            return;
        }

        // Priority 5: A teammate has the ball. PROTECT THEM!
        if (oddball.state() == Oddball.OddballState.CARRIED && self.getTeam() == getPlayerTeam(allPlayers, oddball.carrierId())) {
            self.setCurrentState(AIPlayer.AIState.WANDERING);
            // Wander near the carrier to act as a bodyguard
            self.setObjectiveTargetPoint(oddball.position());
            return;
        }

        // Default fallback
        self.setCurrentState(AIPlayer.AIState.WANDERING);
    }

    private int getPlayerTeam(Collection<Player> players, String playerId) {
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .map(Player::getTeam)
                .orElse(0); // Return 0 if not found
    }
}