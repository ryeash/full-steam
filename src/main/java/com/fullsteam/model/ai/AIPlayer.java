package com.fullsteam.model.ai;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Hazard;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
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

        // --- Apply Steering Forces (in order of priority) ---
        // 1. High-priority: Avoid dangerous hazards.
        Vector2D hazardForce = calculateHazardAvoidanceForce(hazards);
        applyForce(hazardForce);

        // 2. Mid-priority: React to power-ups (avoid powered-up enemies, seek useful items).
        Vector2D powerUpForce = calculatePowerUpInfluenceForce(gameState);
        applyForce(powerUpForce);

        // 3. Let the strategy determine the primary objective (attack, flee, capture)
        aiStrategy.updateAIState(this, gameState);

        // 4. Execute movement logic based on the current state, which adds more forces
        performMovement(obstacles);

        // 5. Independently check for and execute shooting logic against any visible enemy
        Optional<ShootAction> shootAction = checkForShootingOpportunity(allPlayers, obstacles);

        // 6. Update player physics using steering
        // Update velocity by adding acceleration
        setVelocity(getVelocity().add(this.acceleration).limit(getSpeed()));
        // Update position based on new velocity
        super.update();

        // 7. Return the shoot action if any
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
        // Use a "feeler" to detect potential collisions ahead of the AI
        double feelerLength = 40.0 + (getSpeed() * 10); // Dynamic feeler based on speed
        Vector2D feelerEnd = getCenter().add(desiredDirection.multiply(feelerLength));
        Obstacle blockingObstacle = findBlockingObstacle(getCenter(), feelerEnd, obstacles);

        // If the path ahead is clear, continue in the desired direction
        if (blockingObstacle == null) {
            return desiredDirection;
        }

        // --- Wall Sliding Logic ---
        // The path is blocked, so we need to slide along the obstacle.

        // 1. Find the closest point on the obstacle's perimeter to the AI.
        // This gives us a reference point for the collision.
        Vector2D closestPointOnObstacle = findClosestPointOnObstacle(getCenter(), blockingObstacle);

        // 2. Calculate a vector pointing away from the obstacle. This acts as the "normal" to the wall.
        Vector2D avoidanceDirection = getCenter().subtract(closestPointOnObstacle).normalize();

        // 3. Project the AI's desired direction onto the avoidance direction (the normal).
        double projection = desiredDirection.dot(avoidanceDirection);

        // 4. The slide direction is calculated by subtracting the projection from the desired direction.
        // This effectively removes the component of movement that would go "into" the wall,
        // leaving only the component that runs parallel to it.
        Vector2D slideDirection = desiredDirection.subtract(avoidanceDirection.multiply(projection));

        // 5. Return the normalized slide direction. This is the new direction for the AI to follow.
        return slideDirection.normalize();
    }

    /**
     * Finds the point on an obstacle's perimeter that is closest to a given point.
     *
     * @param point    The point to check from.
     * @param obstacle The obstacle to check against.
     * @return The closest point on the obstacle's edges.
     */
    private Vector2D findClosestPointOnObstacle(Vector2D point, Obstacle obstacle) {
        Vector2D closestPoint = null;
        double minDistanceSq = Double.MAX_VALUE;
        List<Vector2D> vertices = obstacle.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            Vector2D p1 = vertices.get(i);
            Vector2D p2 = vertices.get((i + 1) % vertices.size()); // Wrap around for the last edge

            Vector2D closestPointOnSegment = getClosestPointOnLineSegment(point, p1, p2);
            double distanceSq = point.distanceSq(closestPointOnSegment);

            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestPoint = closestPointOnSegment;
            }
        }
        return closestPoint;
    }

    /**
     * Calculates the closest point on a line segment to a given point.
     *
     * @param p The point.
     * @param a The start of the line segment.
     * @param b The end of the line segment.
     * @return The closest point on the segment [a, b] to p.
     */
    private Vector2D getClosestPointOnLineSegment(Vector2D p, Vector2D a, Vector2D b) {
        Vector2D ap = p.subtract(a);
        Vector2D ab = b.subtract(a);
        double ab2 = ab.x() * ab.x() + ab.y() * ab.y();
        if (ab2 == 0.0) { // a and b are the same point
            return a;
        }
        double ap_dot_ab = ap.dot(ab);
        double t = ap_dot_ab / ab2;

        // Clamp t to the range [0, 1] to stay on the segment
        if (t < 0.0) {
            return a;
        } else if (t > 1.0) {
            return b;
        }
        return a.add(ab.multiply(t));
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

    /**
     * Calculates a steering force based on nearby power-ups and powered-up enemies.
     * - It creates an avoidance force to flee from enemies with ARMOR or DAMAGE_BOOST.
     * - It creates an attraction force to seek out valuable power-ups.
     *
     * @param gameState The current state of the game.
     * @return A steering force vector representing the influence of power-ups.
     */
    private Vector2D calculatePowerUpInfluenceForce(GameState gameState) {
        Vector2D totalInfluenceForce = Vector2D.ZERO;
        long currentTime = System.currentTimeMillis();

        // 1. Avoid powered-up enemies
        for (Player player : gameState.players()) {
            if (player.getTeam() == this.getTeam() || player.isDead()) {
                continue;
            }

            boolean isThreat = player.getArmorUpEndTime() > currentTime || player.getDamageBoostEndTime() > currentTime;
            if (isThreat) {
                double distanceSq = getCenter().distanceSq(player.getCenter());
                double threatRadius = 400; // Start avoiding from 400px away
                if (distanceSq < threatRadius * threatRadius) {
                    Vector2D fleeDirection = getCenter().subtract(player.getCenter());
                    // The closer the threat, the stronger the force
                    double strength = 1.0 - (Math.sqrt(distanceSq) / threatRadius);
                    // This is a high-priority action, so give it a strong weight
                    Vector2D avoidanceForce = fleeDirection.normalize().multiply(strength * AI_MAX_FORCE * 2.5);
                    totalInfluenceForce = totalInfluenceForce.add(avoidanceForce);
                }
            }
        }

        // 2. Seek valuable power-ups
        if (gameState.powerUps() != null) {
            for (PowerUp powerUp : gameState.powerUps()) {
                double distanceSq = getCenter().distanceSq(powerUp.getPosition());
                double seekRadius = 500; // Only consider power-ups within 500px
                if (distanceSq < seekRadius * seekRadius) {
                    double weight = 1.0; // Default attraction
                    if (powerUp.getType() == PowerUpType.HEALTH_PACK) {
                        // Very attractive if health is low
                        weight = 2.5 * (1.0 - (getCurrentHealth() / getMaxHealth()));
                    } else if (powerUp.getType() == PowerUpType.ARMOR_UP || powerUp.getType() == PowerUpType.DAMAGE_BOOST) {
                        weight = 1.5; // Always attractive
                    }

                    if (weight > 0.1) { // Only bother if it's somewhat attractive
                        Vector2D seekDirection = powerUp.getPosition().subtract(getCenter());
                        double strength = 1.0 - (Math.sqrt(distanceSq) / seekRadius);
                        Vector2D attractionForce = seekDirection.normalize().multiply(strength * AI_MAX_FORCE * weight);
                        totalInfluenceForce = totalInfluenceForce.add(attractionForce);
                    }
                }
            }
        }

        return totalInfluenceForce;
    }
}
