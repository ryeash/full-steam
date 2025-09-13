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
    private static final double ZOMBIE_SPEED_VARIATION = DEFAULT_PLAYER_SPEED / 4.0;
    private static final double CLOSE_RANGE = 100.0;
    private static final long CHARGE_COOLDOWN = 5000;

    private final double zombieSpeed;
    private long lastChargeTime;
    private boolean isCharging;

    public ZombiePlayer(long id, double x, double y, int team, double baseSpeed) {
        super(id, x, y, team, new ZombieStrategy(), AIArchetype.WARRIOR);
        this.zombieSpeed = Math.max(baseSpeed + ThreadLocalRandom.current().nextDouble(-ZOMBIE_SPEED_VARIATION, ZOMBIE_SPEED_VARIATION), .03);
        setWeapon(WeaponFactory.ZOMBIE_CLAW);
        setSpeed(zombieSpeed);
    }

    @Override
    public PlayerInput generateInput(GameState gameState, SpatialGrid<Targetable> playerGrid, long delta) {
        PlayerInput input = new PlayerInput();
        if (isDead()) {
            return input;
        }
        Optional<Player> nearestHuman = findNearestHuman(gameState);
        if (nearestHuman.isPresent()) {
            Player target = nearestHuman.get();
            setCurrentTarget(target);
            updateChargingBehavior(target);
            generateZombieMovementInput(target, input);
            generateZombieAttackInput(target, input);
        } else {
            generateWanderInput(input);
        }
        return input;
    }

    private void updateChargingBehavior(Player target) {
        long currentTime = System.currentTimeMillis();
        double distanceToTarget = position().distance(target.position());
        if (!isCharging && currentTime - lastChargeTime > CHARGE_COOLDOWN) {
            if (distanceToTarget < CLOSE_RANGE * 2) {
                isCharging = true;
                setSpeed(zombieSpeed * 1.5);
            }
        }
        if (isCharging && distanceToTarget <= CLOSE_RANGE) {
            isCharging = false;
            setSpeed(zombieSpeed);
            lastChargeTime = currentTime;
        }
    }

    private void generateZombieMovementInput(Player target, PlayerInput input) {
        Vector2D directionToTarget = target.position().subtract(position());
        if (directionToTarget.magnitudeSq() > 0.01) {
            Vector2D normalized = directionToTarget.normalize();
            input.setMoveX(normalized.x());
            input.setMoveY(normalized.y());
            aimInDirection(directionToTarget);
            input.setMouseX(getMouseX());
            input.setMouseY(getMouseY());
        }
    }

    private void generateZombieAttackInput(Player target, PlayerInput input) {
        double distanceToTarget = position().distance(target.position());
        if (distanceToTarget <= getWeapon().getBulletRange()) {
            input.setFire(true);
        }
    }

    private void generateWanderInput(PlayerInput input) {
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
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
