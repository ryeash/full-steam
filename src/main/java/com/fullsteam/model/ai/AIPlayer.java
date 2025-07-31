package com.fullsteam.model.ai;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.RandomNames;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.BASE_AIM_INACCURACY_RADIANS;
import static com.fullsteam.Config.BASE_STRAFE_CHANCE;
import static com.fullsteam.Config.BASE_STRAFE_INTERVAL_MS;
import static com.fullsteam.Config.WANDER_DIRECTION_CHANGE_INTERVAL;

public class AIPlayer extends Player {

    public enum AIState {
        WANDERING,
        ATTACKING,
        FLEEING,
        CAPTURING_OBJECTIVE
    }

    private transient AIState currentState = AIState.WANDERING;
    private transient Player currentTarget;
    private transient Vector2D objectiveTargetPoint;
    private transient final IAIStrategy aiStrategy;

    private transient long lastWanderDirectionChangeTime;
    private transient long lastStrafeTime;
    private transient boolean strafeRight = true;
    private transient long timeTargetAcquired;

    // --- AI "Personality" Traits ---
    private transient final long reactionTimeMs;
    private transient final double aimInaccuracyRadians;
    private transient final long strafeInterval;
    private transient final double strafeChance;

    public record ShootAction(double directionX, double directionY) {
    }

    public AIPlayer(String id, double x, double y, int team, IAIStrategy aiStrategy) {
        super(id, "AI - " + RandomNames.randomName(), x, y, team, WeaponFactory.getRandomWeapon());
        this.aiStrategy = aiStrategy;
        this.reactionTimeMs = Config.BASE_REACTION_TIME_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 50);
        this.aimInaccuracyRadians = Math.max(0.01, BASE_AIM_INACCURACY_RADIANS + (ThreadLocalRandom.current().nextDouble() - 0.4) * 0.04);
        this.strafeInterval = BASE_STRAFE_INTERVAL_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 300);
        this.strafeChance = Math.max(0.1, BASE_STRAFE_CHANCE + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2);
    }

    /**
     * The main update loop for the AI. It decides on a behavior, executes it,
     * and returns an optional action for the GameStateManager to perform.
     *
     * @param allPlayers A collection of all players currently in the game.
     * @param obstacles  A list of all obstacles on the map.
     * @param gameInfo   The current game mode's state information.
     * @return An Optional containing a ShootAction if the AI decides to shoot.
     */
    public Optional<ShootAction> update(Collection<Player> allPlayers, List<Obstacle> obstacles, GameInfo gameInfo) {
        if (isDead()) {
            setVelocityX(0);
            setVelocityY(0);
            super.update();
            return Optional.empty();
        }

        aiStrategy.updateAIState(this, allPlayers, gameInfo);

        Optional<ShootAction> shootAction = Optional.empty();
        switch (currentState) {
            case ATTACKING:
                shootAction = performAttackBehavior(obstacles);
                break;
            case CAPTURING_OBJECTIVE:
                if (this.objectiveTargetPoint != null) {
                    moveTowardsPoint(this.objectiveTargetPoint.x(), this.objectiveTargetPoint.y(), obstacles);
                } else {
                    performWanderBehavior(obstacles);
                }
                break;
            case FLEEING:
                if (this.objectiveTargetPoint != null) {
                    // Fleeing logic doesn't need complex avoidance, direct is fine.
                    moveAwayFrom(this.objectiveTargetPoint, obstacles);
                } else {
                    performWanderBehavior(obstacles);
                }
                break;
            case WANDERING:
                if (objectiveTargetPoint != null) {
                    performWanderNearPoint(objectiveTargetPoint, obstacles);
                } else {
                    performWanderBehavior(obstacles);
                }
                break;
        }
        super.update();
        return shootAction;
    }

    public void setCurrentState(AIState state) {
        this.currentState = state;
    }

    public void setObjectiveTargetPoint(Vector2D point) {
        this.objectiveTargetPoint = point;
    }

    public void setCurrentTarget(Player target) {
        if (this.currentTarget != target) {
            this.timeTargetAcquired = System.currentTimeMillis();
        }
        this.currentTarget = target;
    }

    public void setNewTarget(Player target) {
        if (this.currentTarget != target) {
            this.timeTargetAcquired = System.currentTimeMillis();
        }
        this.currentTarget = target;
    }


    private void performWanderNearPoint(Vector2D point, List<Obstacle> obstacles) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWanderDirectionChangeTime > WANDER_DIRECTION_CHANGE_INTERVAL || (getVelocityX() == 0 && getVelocityY() == 0)) {
            double angleToCenter = Math.atan2(point.y() - getY(), point.x() - getX());
            double randomOffset = (ThreadLocalRandom.current().nextDouble() - 0.5) * Math.PI;
            Vector2D desiredDirection = new Vector2D(Math.cos(angleToCenter + randomOffset), Math.sin(angleToCenter + randomOffset));
            Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
            setVelocityX(finalDirection.x() * getSpeed());
            setVelocityY(finalDirection.y() * getSpeed());
            lastWanderDirectionChangeTime = currentTime;
        }
    }

    private void moveTowardsPoint(double targetX, double targetY, List<Obstacle> obstacles) {
        double dx = targetX - getX();
        double dy = targetY - getY();
        double distance = Math.sqrt(dx * dx + dy * dy);

        // Move until very close
        if (distance < 10) {
            setVelocityX(0);
            setVelocityY(0);
            return;
        }

        Vector2D desiredDirection = new Vector2D(dx / distance, dy / distance);
        Vector2D finalDirection = findClearPath(desiredDirection, obstacles);

        setVelocityX(finalDirection.x() * getSpeed());
        setVelocityY(finalDirection.y() * getSpeed());
    }

    private Optional<ShootAction> performAttackBehavior(List<Obstacle> obstacles) {
        if (currentTarget == null) {
            currentState = AIState.WANDERING;
            return Optional.empty();
        }

        if (isReloading()) {
            moveAwayFrom(currentTarget.getX(), currentTarget.getY(), obstacles);
            return Optional.empty();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStrafeTime > this.strafeInterval) {
            lastStrafeTime = currentTime;
            strafeRight = ThreadLocalRandom.current().nextBoolean();
        }

        if (ThreadLocalRandom.current().nextDouble() < this.strafeChance) {
            moveStrafe();
        } else {
            moveTowardsPoint(currentTarget.getX(), currentTarget.getY(), obstacles);
        }

        if (currentTime - timeTargetAcquired < this.reactionTimeMs) {
            return Optional.empty();
        }

        if (findBlockingObstacle(getCenter(), currentTarget.getCenter(), obstacles) != null) {
            return Optional.empty();
        }

        double dx = currentTarget.getX() - getX();
        double dy = currentTarget.getY() - getY();
        double distanceSq = dx * dx + dy * dy;
        double attackRange = getWeapon().getBulletRange();

        if (distanceSq < attackRange * attackRange && canShoot()) {
            return Optional.of(shootWithInaccuracy(dx, dy));
        }

        return Optional.empty();
    }

    private void moveAwayFrom(Vector2D v, List<Obstacle> obstacles) {
        moveAwayFrom(v.x(), v.y(), obstacles);
    }

    private void moveAwayFrom(double targetX, double targetY, List<Obstacle> obstacles) {
        double dx = getX() - targetX;
        double dy = getY() - targetY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > 0) {
            Vector2D desiredDirection = new Vector2D(dx / distance, dy / distance);
            // Use the pathfinding logic to find a clear retreat path.
            Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
            setVelocityX(finalDirection.x() * getSpeed());
            setVelocityY(finalDirection.y() * getSpeed());
        }
    }

    private void performWanderBehavior(List<Obstacle> obstacles) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWanderDirectionChangeTime > WANDER_DIRECTION_CHANGE_INTERVAL || (getVelocityX() == 0 && getVelocityY() == 0)) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            Vector2D desiredDirection = new Vector2D(Math.cos(angle), Math.sin(angle));
            Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
            setVelocityX(finalDirection.x() * getSpeed());
            setVelocityY(finalDirection.y() * getSpeed());
            lastWanderDirectionChangeTime = currentTime;
        }
    }

    /**
     * Helper method to set the AI's velocity to move directly away from a specific point.
     * This is used for retreating while reloading and fleeing.
     */
    private void moveAwayFrom(double targetX, double targetY) {
        double dx = getX() - targetX;
        double dy = getY() - targetY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance > 0) {
            setVelocityX((dx / distance) * getSpeed());
            setVelocityY((dy / distance) * getSpeed());
        }
    }

    /**
     * Creates a ShootAction with a slight random inaccuracy based on the AI's "personality".
     */
    private ShootAction shootWithInaccuracy(double dx, double dy) {
        double perfectAngle = Math.atan2(dy, dx);
        double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * this.aimInaccuracyRadians;
        double finalAngle = perfectAngle + inaccuracy;
        return new ShootAction(Math.cos(finalAngle), Math.sin(finalAngle));
    }

    /**
     * Sets velocity to move perpendicular to the target, creating a strafing motion.
     */
    private void moveStrafe() {
        if (currentTarget == null) {
            return;
        }
        double dx = currentTarget.getX() - getX();
        double dy = currentTarget.getY() - getY();

        double strafeDx = -dy;
        double strafeDy = dx;

        if (!strafeRight) {
            strafeDx = -strafeDx;
            strafeDy = -strafeDy;
        }

        double length = Math.sqrt(strafeDx * strafeDx + strafeDy * strafeDy);
        if (length > 0) {
            setVelocityX((strafeDx / length) * getSpeed());
            setVelocityY((strafeDy / length) * getSpeed());
        }
    }

    /**
     * Checks if a direct path to a target is blocked by any obstacle.
     *
     * @param start     The starting point of the path.
     * @param end       The ending point of the path.
     * @param obstacles The list of all obstacles.
     * @return The first obstacle found blocking the path, or null if the path is clear.
     */
    private Obstacle findBlockingObstacle(Vector2D start, Vector2D end, List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (CollisionUtils.checkLinePolygonCollision(start, end, obstacle.vertices())) {
                return obstacle;
            }
        }
        return null;
    }

    /**
     * Finds a clear direction of movement by testing the desired direction and then
     * incrementally steering left and right if it's blocked.
     *
     * @param desiredDirection The ideal direction of travel.
     * @param obstacles        The list of all obstacles.
     * @return A clear direction vector, or a reversed direction as a last resort.
     */
    private Vector2D findClearPath(Vector2D desiredDirection, List<Obstacle> obstacles) {
        // The "feeler" checks a short distance ahead.
        double feelerLength = getSpeed() * 15;

        // 1. Check if the desired path is already clear.
        Vector2D feelerEnd = getCenter().add(desiredDirection.scale(feelerLength));
        if (findBlockingObstacle(getCenter(), feelerEnd, obstacles) == null) {
            return desiredDirection;
        }

        // 2. If blocked, incrementally search for a clear path by rotating the feeler.
        // This creates a smoother "steering" behavior around obstacles.
        double steeringAngleIncrement = Math.toRadians(15); // Try every 15 degrees
        for (int i = 1; i <= 6; i++) { // Check up to 90 degrees left/right
            // Try turning right
            Vector2D rightTurnDirection = desiredDirection.rotate(steeringAngleIncrement * i);
            feelerEnd = getCenter().add(rightTurnDirection.scale(feelerLength));
            if (findBlockingObstacle(getCenter(), feelerEnd, obstacles) == null) {
                return rightTurnDirection; // Found a clear path to the right
            }

            // Try turning left
            Vector2D leftTurnDirection = desiredDirection.rotate(-steeringAngleIncrement * i);
            feelerEnd = getCenter().add(leftTurnDirection.scale(feelerLength));
            if (findBlockingObstacle(getCenter(), feelerEnd, obstacles) == null) {
                return leftTurnDirection; // Found a clear path to the left
            }
        }

        // 3. If all forward-facing paths are blocked, move backward away from the desired direction.
        return desiredDirection.scale(-0.5);
    }
}