package com.fullsteam.model.ai;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Hazard;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.RandomNames;
import com.fullsteam.model.Vector2D;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.AI_MAX_FORCE;
import static com.fullsteam.Config.BASE_AIM_INACCURACY_RADIANS;
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
    private transient Vector2D wanderTarget;
    private transient final IAIStrategy aiStrategy;
    private transient final AIArchetype archetype;

    private transient long lastWanderDirectionChangeTime;
    private transient long lastStrafeTime;
    private transient boolean strafeRight = true;
    private transient Vector2D acceleration = Vector2D.ZERO;
    private transient long timeTargetAcquired;

    // --- AI "Personality" Traits ---
    private transient final long reactionTimeMs;
    private transient final double aimInaccuracyRadians;
    private transient final long strafeInterval;

    public record ShootAction(double directionX, double directionY) {
    }

    public AIPlayer(String id, double x, double y, int team, IAIStrategy aiStrategy, AIArchetype archetype) {
        super(id, "AI - " + RandomNames.randomName(), x, y, team, WeaponFactory.getRandomWeapon());
        this.aiStrategy = aiStrategy;
        this.archetype = archetype;
        this.reactionTimeMs = Config.BASE_REACTION_TIME_MS + (long) (ThreadLocalRandom.current().nextDouble() * 50);
        this.aimInaccuracyRadians = Math.max(0.01, BASE_AIM_INACCURACY_RADIANS + (ThreadLocalRandom.current().nextDouble() - 0.4) * 0.04);
        this.strafeInterval = BASE_STRAFE_INTERVAL_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 300);
    }

    public AIArchetype archetype() {
        return archetype;
    }

    /**
     * The main update loop for the AI. It decouples movement from shooting, allowing the AI
     * to engage enemies while performing other actions.
     *
     * @param gameState The current game mode's state information.
     * @return An Optional containing a ShootAction if the AI decides to shoot.
     */
    public Optional<ShootAction> update(GameState gameState) {
        if (isDead()) {
            setVelocity(Vector2D.ZERO);
            super.update();
            return Optional.empty();
        }
        Collection<Player> allPlayers = gameState.players();
        List<Obstacle> obstacles = gameState.obstacles();
        List<Hazard> hazards = gameState.hazards();

        // Reset acceleration at the start of each frame
        this.acceleration = Vector2D.ZERO;

        // --- Apply Steering Forces ---
        // 1. High-priority: Avoid dangerous hazards. This force is applied first.
        Vector2D hazardForce = calculateHazardAvoidanceForce(hazards);
        applyForce(hazardForce);

        // 2. Let the strategy determine the primary objective (attack, flee, capture)
        aiStrategy.updateAIState(this, gameState);

        // 3. Execute movement logic based on the current state, which adds more forces
        performMovement(obstacles);

        // 4. Independently check for and execute shooting logic against any visible enemy
        Optional<ShootAction> shootAction = checkForShootingOpportunity(allPlayers, obstacles);

        // 4. Update player physics using steering
        // Update velocity by adding acceleration
        setVelocity(getVelocity().add(this.acceleration).limit(getSpeed()));
        // Update position based on new velocity
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
                // Decide whether to strafe or advance on the target
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastStrafeTime > this.strafeInterval) {
                    lastStrafeTime = currentTime;
                    strafeRight = ThreadLocalRandom.current().nextBoolean();
                }

                if (isReloading() || getCenter().distanceSq(currentTarget.getCenter()) < 150 * 150) {
                    // If reloading or too close, strafe to be evasive
                    strafe(obstacles);
                } else {
                    // Otherwise, seek the target
                    seek(currentTarget.getCenter(), obstacles);
                }
                break;
            case CAPTURING_OBJECTIVE:
                seek(this.objectiveTargetPoint, obstacles);
                break;
            case FLEEING:
                flee(this.objectiveTargetPoint, obstacles);
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
        if (!canShoot()) {
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

    /**
     * Applies a steering force to the AI's acceleration.
     */
    private void applyForce(Vector2D force) {
        this.acceleration = this.acceleration.add(force);
    }

    /**
     * A steering behavior that directs the AI towards a target point.
     * It incorporates simple obstacle avoidance.
     */
    private void seek(Vector2D target, List<Obstacle> obstacles) {
        if (target == null) {
            performWanderBehavior(obstacles);
            return;
        }
        // 1. Calculate the desired velocity (a vector pointing from us to the target)
        Vector2D desiredDirection = target.subtract(getCenter()).normalize();

        // 2. Use the existing obstacle avoidance to adjust the desired direction
        Vector2D avoidanceDirection = findClearPath(desiredDirection, obstacles);
        Vector2D desiredVelocity = avoidanceDirection.multiply(getSpeed());

        // 3. Calculate the steering force (the force required to change current velocity to desired velocity)
        Vector2D steer = desiredVelocity.subtract(getVelocity());
        steer = steer.limit(AI_MAX_FORCE); // Limit the force to our turning ability

        // 4. Apply the force
        applyForce(steer);
    }

    /**
     * A steering behavior that directs the AI away from a target point.
     */
    private void flee(Vector2D target, List<Obstacle> obstacles) {
        if (target == null) {
            performWanderBehavior(obstacles);
            return;
        }
        // The desired velocity is the opposite of seek
        Vector2D desiredDirection = getCenter().subtract(target).normalize();
        Vector2D avoidanceDirection = findClearPath(desiredDirection, obstacles);
        Vector2D desiredVelocity = avoidanceDirection.multiply(getSpeed());

        Vector2D steer = desiredVelocity.subtract(getVelocity());
        steer = steer.limit(AI_MAX_FORCE);
        applyForce(steer);
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

    private void performWanderBehavior(List<Obstacle> obstacles) {
        // If we don't have a wander target or we've reached it, pick a new one.
        if (wanderTarget == null || getCenter().distanceSq(wanderTarget) < 100 * 100) { // 100px radius
            double x = ThreadLocalRandom.current().nextDouble(50, Config.GAME_WIDTH - 50);
            double y = ThreadLocalRandom.current().nextDouble(50, Config.GAME_HEIGHT - 50);
            this.wanderTarget = new Vector2D(x, y);
        }
        // Smoothly move towards the wander target.
        seek(this.wanderTarget, obstacles);
    }

    private ShootAction shootWithInaccuracy(double dx, double dy) {
        double perfectAngle = Math.atan2(dy, dx);
        double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * this.aimInaccuracyRadians;
        double finalAngle = perfectAngle + inaccuracy;
        return new ShootAction(Math.cos(finalAngle), Math.sin(finalAngle));
    }

    private void strafe(List<Obstacle> obstacles) {
        if (currentTarget == null) {
            performWanderBehavior(obstacles);
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

        Vector2D desiredDirection = new Vector2D(strafeDx, strafeDy).normalize();
        Vector2D avoidanceDirection = findClearPath(desiredDirection, obstacles);
        Vector2D desiredVelocity = avoidanceDirection.multiply(getSpeed());

        Vector2D steer = desiredVelocity.subtract(getVelocity());
        applyForce(steer.limit(AI_MAX_FORCE)); // Apply a strong force for responsive strafing
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

    /**
     * Calculates a steering force to move the AI away from nearby hazards, especially damaging ones.
     * The force is stronger the closer the AI is to the hazard's center.
     *
     * @param hazards A list of all hazards on the map.
     * @return A steering force vector.
     */
    private Vector2D calculateHazardAvoidanceForce(List<Hazard> hazards) {
        Vector2D totalAvoidanceForce = Vector2D.ZERO;
        if (hazards == null) {
            return totalAvoidanceForce;
        }

        for (Hazard hazard : hazards) {
            // The "awareness" radius is slightly larger than the hazard itself.
            double awarenessRadius = hazard.radius() + 40; // Be aware of it from 40px away
            double distanceSq = getCenter().distanceSq(hazard.position());

            if (distanceSq < awarenessRadius * awarenessRadius) {
                // We are near or inside a hazard. Calculate a force to flee from it.
                Vector2D fleeDirection = getCenter().subtract(hazard.position());

                // The closer we are, the stronger the force should be.
                double strength = 1.0 - (Math.sqrt(distanceSq) / awarenessRadius);
                double weight = (hazard.type() == Hazard.Type.DAMAGE) ? 4.0 : 1.0;
                Vector2D avoidanceForce = fleeDirection.normalize().multiply(strength * AI_MAX_FORCE * weight);
                totalAvoidanceForce = totalAvoidanceForce.add(avoidanceForce);
            }
        }
        return totalAvoidanceForce;
    }
}