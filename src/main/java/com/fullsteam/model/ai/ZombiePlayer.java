package com.fullsteam.model.ai;

import com.fullsteam.Config;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Vector2D;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.DEFAULT_PLAYER_SPEED;

public class ZombiePlayer extends AIPlayer {
    // Zombie-specific constants
    private static final double ZOMBIE_SPEED_VARIATION = DEFAULT_PLAYER_SPEED / 4.0;
    private static final double CLOSE_RANGE = 100.0;
    private static final long CHARGE_COOLDOWN = 5000; // 5 seconds between charges

    private final double zombieSpeed;
    private long lastChargeTime;
    private boolean isCharging;

    public ZombiePlayer(long id, double x, double y, int team, double baseSpeed) {
        // Zombies always use melee weapons and have randomized speed
        super(id, x, y, team, new ZombieStrategy(), AIArchetype.WARRIOR);
        this.zombieSpeed = baseSpeed + ThreadLocalRandom.current().nextDouble(-ZOMBIE_SPEED_VARIATION, ZOMBIE_SPEED_VARIATION);
        setWeapon(WeaponFactory.ZOMBIE_CLAW);
        setSpeed(zombieSpeed);
    }

    @Override
    public Optional<ShootAction> update(GameState gameState, SpatialGrid<Targetable> playerGrid, long delta) {
        // Override the standard update to implement zombie-specific behavior
        if (isDead()) {
            setVelocity(Vector2D.ZERO);
            super.update(delta);
            return Optional.empty();
        }
        if (getVelocity().magnitudeSq() > 0.01) {
            aimInDirection(getVelocity());
        }

        // Zombies always try to find the nearest human player
        Optional<Player> nearestHuman = findNearestHuman(gameState);
        if (nearestHuman.isPresent()) {
            Player target = nearestHuman.get();
            setCurrentTarget(target);
            aimInDirection(target.position());

            // Update charging state
            long currentTime = System.currentTimeMillis();
            if (!isCharging && currentTime - lastChargeTime > CHARGE_COOLDOWN) {
                double distanceToTarget = position().distance(target.position());
                if (distanceToTarget < CLOSE_RANGE * 2) {
                    isCharging = true;
                    setSpeed(zombieSpeed * 1.5); // 50% speed boost during charge
                }
            }

            // End charge when close to target
            if (isCharging && position().distance(target.position()) <= CLOSE_RANGE) {
                isCharging = false;
                setSpeed(zombieSpeed);
                lastChargeTime = currentTime;
            }
        }

        // Use the standard movement and targeting logic
        Optional<ShootAction> action = super.update(gameState, playerGrid, delta);

        // Zombies always try to attack when in range
        if (action.isEmpty() && getCurrentTarget() != null) {
            double distanceToTarget = position().distance(getCurrentTarget().position());
            if (distanceToTarget <= getWeapon().getBulletRange()) {
                Vector2D directionToTarget = getCurrentTarget().position().subtract(position()).normalize();
                return Optional.of(new ShootAction(directionToTarget.x(), directionToTarget.y()));
            }
        }

        return action;
    }

    private Optional<Player> findNearestHuman(GameState gameState) {
        Player nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Player player : gameState.players()) {
            if (player.getTeam() != getTeam() && !player.isDead()) {
                double distSq = position().distanceSquared(player.position());
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = player;
                }
            }
        }

        return Optional.ofNullable(nearest);
    }

    public Player getCurrentTarget() {
        return super.currentTarget;
    }
}

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