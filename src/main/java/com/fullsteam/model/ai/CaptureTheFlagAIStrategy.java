package com.fullsteam.model.ai;

import com.fullsteam.model.Flag;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;

public class CaptureTheFlagAIStrategy implements IAIStrategy {
    @Override
    public void updateAIState(AIPlayer self, Collection<Player> allPlayers, GameInfo gameInfo) {
        if (gameInfo instanceof CaptureTheFlagInfo ctf) {
            Player closestEnemy = findClosestEnemy(self, allPlayers);
            if (closestEnemy != null) {
                self.setNewTarget(closestEnemy);
                self.setCurrentState(AIPlayer.AIState.ATTACKING);
                return;
            }

            self.setCurrentTarget(null);
            self.setObjectiveTargetPoint(null);

            Flag myFlag = self.getTeam() == 1 ? ctf.getTeam1Flag() : ctf.getTeam2Flag();
            Flag enemyFlag = self.getTeam() == 1 ? ctf.getTeam2Flag() : ctf.getTeam1Flag();

            if (enemyFlag.state() == Flag.FlagState.CARRIED && self.getId().equals(enemyFlag.carrierId())) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(myFlag.basePosition()); // Go score!
                return;
            }
            if (myFlag.state() == Flag.FlagState.DROPPED) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(myFlag.position()); // Recover our flag!
                return;
            }
            if (enemyFlag.state() == Flag.FlagState.AT_BASE) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(enemyFlag.basePosition()); // Priority 3: Enemy flag is home, go steal it!
                return;
            }
            if (enemyFlag.state() == Flag.FlagState.DROPPED) {
                self.setCurrentState(AIPlayer.AIState.CAPTURING_OBJECTIVE);
                self.setObjectiveTargetPoint(enemyFlag.position()); // Priority 4: Enemy flag is dropped, grab it!
                return;
            }

            self.setCurrentState(AIPlayer.AIState.WANDERING); // Priority 5: All is well, defend our flag.
        } else {
            throw new IllegalStateException("wrong AI strategy");
        }
    }
}
