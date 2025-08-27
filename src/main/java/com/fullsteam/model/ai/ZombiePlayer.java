package com.fullsteam.model.ai;

import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
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
    public PlayerInput generateInput(GameState gameState, SpatialGrid<Targetable> playerGrid, long delta) {
        PlayerInput input = new PlayerInput();
        
        if (isDead()) {
            return input; // Return empty input for dead zombies
        }

        // Zombies always try to find the nearest human player
        Optional<Player> nearestHuman = findNearestHuman(gameState);
        if (nearestHuman.isPresent()) {
            Player target = nearestHuman.get();
            setCurrentTarget(target);

            // Update charging state based on distance to target
            updateChargingBehavior(target);

            // Generate movement input toward target
            generateZombieMovementInput(target, input);

            // Generate attack input if in range
            generateZombieAttackInput(target, input);
        } else {
            // No target found - wander around
            generateWanderInput(input);
        }

        return input;
    }

    /**
     * Updates zombie charging behavior based on target proximity
     */
    private void updateChargingBehavior(Player target) {
        long currentTime = System.currentTimeMillis();
        double distanceToTarget = position().distance(target.position());

        // Start charging if not already charging and cooldown is over
        if (!isCharging && currentTime - lastChargeTime > CHARGE_COOLDOWN) {
            if (distanceToTarget < CLOSE_RANGE * 2) {
                isCharging = true;
                setSpeed(zombieSpeed * 1.5); // 50% speed boost during charge
            }
        }

        // End charge when close to target
        if (isCharging && distanceToTarget <= CLOSE_RANGE) {
            isCharging = false;
            setSpeed(zombieSpeed);
            lastChargeTime = currentTime;
        }
    }

    /**
     * Generates movement input to aggressively pursue the target
     */
    private void generateZombieMovementInput(Player target, PlayerInput input) {
        Vector2D directionToTarget = target.position().subtract(position());
        if (directionToTarget.magnitudeSq() > 0.01) {
            Vector2D normalized = directionToTarget.normalize();
            input.setMoveX(normalized.x());
            input.setMoveY(normalized.y());
            
            // Set mouse position for aiming
            aimInDirection(directionToTarget);
            input.setMouseX(getMouseX());
            input.setMouseY(getMouseY());
        }
    }

    /**
     * Generates attack input when target is in range
     */
    private void generateZombieAttackInput(Player target, PlayerInput input) {
        double distanceToTarget = position().distance(target.position());
        
        // Zombies attack aggressively when in range (melee weapons have short range)
        if (distanceToTarget <= getWeapon().getBulletRange()) {
            // Zombies fire continuously when in range - no hesitation
            input.setFire(true);
        }
    }

    /**
     * Generates wandering movement when no target is found
     */
    private void generateWanderInput(PlayerInput input) {
        // Simple random movement when no target
        if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% chance to change direction
            double randomAngle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            Vector2D randomDirection = new Vector2D(Math.cos(randomAngle), Math.sin(randomAngle));
            
            input.setMoveX(randomDirection.x());
            input.setMoveY(randomDirection.y());
            
            aimInDirection(randomDirection);
            input.setMouseX(getMouseX());
            input.setMouseY(getMouseY());
        }
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


}
