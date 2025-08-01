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
    private transient final AIArchetype archetype;

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

    public AIPlayer(String id, double x, double y, int team, IAIStrategy aiStrategy, AIArchetype archetype) {
        super(id, "AI - " + RandomNames.randomName(), x, y, team, WeaponFactory.getRandomWeapon());
        this.aiStrategy = aiStrategy;
        this.archetype = archetype;
        this.reactionTimeMs = Config.BASE_REACTION_TIME_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 50);
        this.aimInaccuracyRadians = Math.max(0.01, BASE_AIM_INACCURACY_RADIANS + (ThreadLocalRandom.current().nextDouble() - 0.4) * 0.04);
        this.strafeInterval = BASE_STRAFE_INTERVAL_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 300);
        this.strafeChance = Math.max(0.1, BASE_STRAFE_CHANCE + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.2);
    }

    public AIArchetype archetype() {
        return archetype;
    }

    /**
     * The main update loop for the AI. It decouples movement from shooting, allowing the AI
     * to engage enemies while performing other actions.
     *
     * @param allPlayers A collection of all players currently in the game.
     * @param obstacles  A list of all obstacles on the map.
     * @param gameInfo   The current game mode's state information.
     * @return An Optional containing a ShootAction if the AI decides to shoot.
     */
    public Optional<ShootAction> update(Collection<Player> allPlayers, List<Obstacle> obstacles, GameInfo gameInfo) {
        if (isDead()) {
            setVelocity(Vector2D.ZERO);
            super.update();
            return Optional.empty();
        }

        // 1. Let the strategy determine the current state, objective, and primary target
        aiStrategy.updateAIState(this, allPlayers, gameInfo);

        // 2. Execute movement logic based on the current state
        performMovement(obstacles);

        // 3. Independently check for and execute shooting logic against any visible enemy
        Optional<ShootAction> shootAction = checkForShootingOpportunity(allPlayers, obstacles);

        // 4. Update player physics (position based on velocity)
        super.update();

        // 5. Return the shoot action if any
        return shootAction;
    }

    /**
     * Handles all AI movement based on its current state (attacking, fleeing, etc.).
     * This method sets the AI's velocity.
     */
    private void performMovement(List<Obstacle> obstacles) {
        switch (currentState) {
            case ATTACKING:
                if (currentTarget == null) {
                    performWanderBehavior(obstacles); // Fallback if target is lost
                    return;
                }
                if (isReloading()) {
                    moveAwayFrom(currentTarget.getCenter(), obstacles); // Retreat while reloading
                    return;
                }
                // Decide whether to strafe or advance on the target
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
    }

    /**
     * Scans for any valid enemy to shoot, independent of the AI's current movement state.
     *
     * @return An Optional ShootAction if a valid target is found and the AI can fire.
     */
    private Optional<ShootAction> checkForShootingOpportunity(Collection<Player> allPlayers, List<Obstacle> obstacles) {
        if (isReloading() || !canShoot()) {
            return Optional.empty();
        }

        Player targetToShoot = findBestShootingTarget(allPlayers, obstacles);
        if (targetToShoot == null) {
            return Optional.empty();
        }

        // If the best target is our primary `currentTarget`, respect the AI's reaction time.
        if (currentTarget == targetToShoot) {
            if (System.currentTimeMillis() - timeTargetAcquired < this.reactionTimeMs) {
                return Optional.empty(); // Still "reacting"
            }
        }

        double dx = targetToShoot.getX() - getX();
        double dy = targetToShoot.getY() - getY();
        return Optional.of(shootWithInaccuracy(dx, dy));
    }

    /**
     * Finds the closest enemy player that is within weapon range and has a clear line of sight.
     *
     * @return The best Player to target, or null if no valid target exists.
     */
    private Player findBestShootingTarget(Collection<Player> allPlayers, List<Obstacle> obstacles) {
        Player bestTarget = null;
        double minDistanceSq = Double.MAX_VALUE;
        double attackRangeSq = getWeapon().getBulletRange() * getWeapon().getBulletRange();

        for (Player potentialTarget : allPlayers) {
            if (potentialTarget.getId().equals(this.getId()) || potentialTarget.isDead() || potentialTarget.getTeam() == this.getTeam()) {
                continue;
            }

            double distanceSq = this.getCenter().distanceSq(potentialTarget.getCenter());

            if (distanceSq < attackRangeSq && distanceSq < minDistanceSq) {
                if (findBlockingObstacle(this.getCenter(), potentialTarget.getCenter(), obstacles) == null) {
                    minDistanceSq = distanceSq;
                    bestTarget = potentialTarget;
                }
            }
        }
        return bestTarget;
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

    private void performWanderNearPoint(Vector2D point, List<Obstacle> obstacles) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWanderDirectionChangeTime > WANDER_DIRECTION_CHANGE_INTERVAL || getVelocity().magnitudeSq() == 0) {
            double angleToCenter = Math.atan2(point.y() - getY(), point.x() - getX());
            double randomOffset = (ThreadLocalRandom.current().nextDouble() - 0.5) * Math.PI;
            Vector2D desiredDirection = new Vector2D(Math.cos(angleToCenter + randomOffset), Math.sin(angleToCenter + randomOffset));
            Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
            setVelocity(finalDirection.multiply(getSpeed()));
            lastWanderDirectionChangeTime = currentTime;
        }
    }

    private void moveTowardsPoint(double targetX, double targetY, List<Obstacle> obstacles) {
        Vector2D targetPoint = new Vector2D(targetX, targetY);
        double distanceSq = getCenter().distanceSq(targetPoint);

        if (distanceSq < 100) { // Stop if very close
            setVelocity(Vector2D.ZERO);
            return;
        }

        Vector2D desiredDirection = targetPoint.add(getCenter().multiply(-1)).normalize();
        Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
        setVelocity(finalDirection.multiply(getSpeed()));
    }

    private void moveAwayFrom(Vector2D v, List<Obstacle> obstacles) {
        Vector2D desiredDirection = getCenter().add(v.multiply(-1)).normalize();
        Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
        setVelocity(finalDirection.multiply(getSpeed()));
    }

    private void performWanderBehavior(List<Obstacle> obstacles) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastWanderDirectionChangeTime > WANDER_DIRECTION_CHANGE_INTERVAL || getVelocity().magnitudeSq() == 0) {
            double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
            Vector2D desiredDirection = new Vector2D(Math.cos(angle), Math.sin(angle));
            Vector2D finalDirection = findClearPath(desiredDirection, obstacles);
            setVelocity(finalDirection.multiply(getSpeed()));
            lastWanderDirectionChangeTime = currentTime;
        }
    }

    private ShootAction shootWithInaccuracy(double dx, double dy) {
        double perfectAngle = Math.atan2(dy, dx);
        double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * this.aimInaccuracyRadians;
        double finalAngle = perfectAngle + inaccuracy;
        return new ShootAction(Math.cos(finalAngle), Math.sin(finalAngle));
    }

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
            setVelocity(new Vector2D((strafeDx / length) * getSpeed(), (strafeDy / length) * getSpeed()));
        }
    }

    private Obstacle findBlockingObstacle(Vector2D start, Vector2D end, List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (CollisionUtils.checkLinePolygonCollision(start, end, obstacle.vertices())) {
                return obstacle;
            }
        }
        return null;
    }

    private Vector2D findClearPath(Vector2D desiredDirection, List<Obstacle> obstacles) {
        double feelerLength = getSpeed() * 15;
        Vector2D feelerEnd = getCenter().add(desiredDirection.multiply(feelerLength));
        if (findBlockingObstacle(getCenter(), feelerEnd, obstacles) == null) {
            return desiredDirection;
        }

        double steeringAngleIncrement = Math.toRadians(15);
        for (int i = 1; i <= 6; i++) {
            Vector2D rightTurnDirection = desiredDirection.rotate(steeringAngleIncrement * i);
            feelerEnd = getCenter().add(rightTurnDirection.multiply(feelerLength));
            if (findBlockingObstacle(getCenter(), feelerEnd, obstacles) == null) {
                return rightTurnDirection;
            }

            Vector2D leftTurnDirection = desiredDirection.rotate(-steeringAngleIncrement * i);
            feelerEnd = getCenter().add(leftTurnDirection.multiply(feelerLength));
            if (findBlockingObstacle(getCenter(), feelerEnd, obstacles) == null) {
                return leftTurnDirection;
            }
        }
        return desiredDirection.multiply(-0.5);
    }
}