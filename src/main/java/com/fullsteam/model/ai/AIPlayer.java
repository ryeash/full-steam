package com.fullsteam.model.ai;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
import com.fullsteam.model.RandomNames;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.AI_DECISION_COOLDOWN_MS;
import static com.fullsteam.Config.AI_MAX_FORCE;
import static com.fullsteam.Config.BASE_AIM_INACCURACY_RADIANS;
import static com.fullsteam.Config.BASE_STRAFE_INTERVAL_MS;
import static com.fullsteam.Config.WANDER_DIRECTION_CHANGE_INTERVAL;

/**
 * Represents an AI-controlled player in the game.
 * This class uses a state machine and steering behaviors to make decisions.
 * All vector calculations are performed using the custom Vector2D class for consistency and robustness.
 */
public class AIPlayer extends Player {

    // Defines the primary action the AI is trying to perform.
    public enum AIState {
        WANDERING,
        ATTACKING,
        FLEEING,
        CAPTURING_OBJECTIVE
    }

    // --- AI State & Strategy ---
    private transient AIState currentState = AIState.WANDERING;
    protected transient Player currentTarget;
    private transient Vector2D objectiveTargetPoint;
    private transient Vector2D wanderTarget;
    private transient final IAIStrategy aiStrategy;
    private transient final AIArchetype archetype;

    // --- Debug/Analytics Fields ---
    private transient long stateChangeTime = System.currentTimeMillis();

    // --- AI Behavior Timing & State ---
    private transient long lastWanderDirectionChangeTime;
    private transient long lastStrafeTime;
    private transient boolean strafeRight = true;
    private transient long timeTargetAcquired;

    // --- Steering Behavior Fields ---
    private transient Vector2D acceleration;

    // --- AI "Personality" Traits ---
    private transient final long reactionTimeMs;
    private transient final double aimInaccuracyRadians;
    private transient final long strafeInterval;
    private transient final double rangeSlew;

    /**
     * Represents a decision to fire the weapon in a specific direction.
     */
    public record ShootAction(double directionX, double directionY) {
    }

    public AIPlayer(long id, double x, double y, int team, IAIStrategy aiStrategy, AIArchetype archetype) {
        super(id, "AI - " + RandomNames.randomName(), x, y, team, WeaponFactory.getRandomWeapon());
        this.aiStrategy = aiStrategy;
        this.archetype = archetype;

        // Initialize personality traits with some randomness
        this.reactionTimeMs = Config.BASE_REACTION_TIME_MS + (long) (ThreadLocalRandom.current().nextDouble() * 50);
        this.aimInaccuracyRadians = Math.max(0.01, BASE_AIM_INACCURACY_RADIANS + (ThreadLocalRandom.current().nextDouble() - 0.4) * 0.04);
        this.strafeInterval = BASE_STRAFE_INTERVAL_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 300);
        this.rangeSlew = ThreadLocalRandom.current().nextGaussian() * 50;
        this.acceleration = Vector2D.ZERO;
    }

    public AIArchetype archetype() {
        return archetype;
    }

    /**
     * The main update loop for the AI. It orchestrates the AI's decision-making process each frame.
     * The process follows a standard steering behavior model:
     * 1. The AI's strategy determines its high-level goal (e.g., attack, defend).
     * 2. Various "steering forces" are calculated based on the environment (obstacles, hazards, power-ups).
     * 3. These forces are combined into a single acceleration vector.
     * 4. The acceleration is used to update the AI's velocity.
     * 5. The AI's position is updated based on its new velocity (handled by the parent Player class).
     * 6. A decision to shoot is made independently of movement.
     *
     * @param gameState  The current game mode's state information.
     * @param playerGrid The spatial grid for proximity queries.
     * @return An Optional containing a {@link ShootAction} if the AI decides to shoot this frame.
     */
    public Optional<ShootAction> update(GameState gameState, SpatialGrid<Targetable> playerGrid, long delta) {
        if (isDead()) {
            setVelocity(Vector2D.ZERO);
            super.update(delta); // Still need to call this to update position based on zero velocity
            return Optional.empty();
        }

        // 1. Reset acceleration for the new frame.
        this.acceleration = Vector2D.ZERO;

        // 2. Calculate and apply all steering forces, from highest to lowest priority.
        // The order is crucial for realistic behavior.
        applySteeringForces(gameState);

        // 3. Update physics based on the final accumulated acceleration.
        updatePhysics(delta);

        // 4. Handle aiming and shooting logic, which is independent of movement.
        return decideOnShooting(gameState, playerGrid);
    }

    /**
     * Calculates all steering forces and applies them to the AI's acceleration.
     * The forces are weighted and prioritized to create believable movement.
     */
    private void applySteeringForces(GameState gameState) {
        // --- Priority 1: Immediate Survival ---
        // Highest priority: A strong, short-range force to avoid getting stuck on walls.
        Vector2D separationForce = calculateObstacleSeparationForce(gameState.obstacles());
        applyForce(separationForce, 5.0); // High weight to override other behaviors

        // High priority: Flee from damaging hazards.
        Vector2D hazardForce = calculateHazardAvoidanceForce(gameState.fieldEffects());
        applyForce(hazardForce, 4.0);

        // --- Priority 2: Tactical Decisions ---
        // Mid priority: React to power-ups (seek good ones, avoid powered-up enemies).
        Vector2D powerUpForce = calculatePowerUpInfluenceForce(gameState);
        applyForce(powerUpForce, 2.5);

        // --- Priority 3: Strategic Goal ---
        // Let the game-mode-specific strategy determine the AI's current state and objective.
        // Only re-evaluate the strategy periodically to prevent indecisive jittering.
        if ((System.currentTimeMillis() - stateChangeTime) > AI_DECISION_COOLDOWN_MS) {
            aiStrategy.updateAIState(this, gameState);
        }

        // Execute the primary movement behavior based on the current state.
        Vector2D objectiveForce = calculateObjectiveForce(gameState.obstacles());
        applyForce(objectiveForce, 1.0);
    }

    /**
     * Updates the AI's velocity based on the accumulated acceleration, then calls the parent
     * method to update its position.
     */
    private void updatePhysics(long delta) {
        double deltaSeconds = delta / 1000.0;
        // Update velocity by adding acceleration (scaled by delta time), and cap it at the AI's max speed.
        Vector2D newVelocity = getVelocity().add(this.acceleration.multiply(deltaSeconds)).limit(getSpeed());
        setVelocity(newVelocity);

        // Update position based on the new velocity.
        super.update(delta);
    }

    private Optional<ShootAction> decideOnShooting(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        Optional<Targetable> bestTargetInfo = findBestTarget(gameState, playerGrid);

        if (bestTargetInfo.isEmpty()) {
            // If no target, but we are moving, aim in the direction of movement.
            if (getVelocity().magnitudeSq() > 0.01) {
                aimInDirection(getVelocity());
            }
            return Optional.empty();
        }

        Targetable finalTarget = bestTargetInfo.get();
        Vector2D directionToTarget = finalTarget.position().subtract(position());

        // Aim at the target.
        aimInDirection(directionToTarget);

        // Check if we are able to fire (not reloading, cooldown is over, etc.).
        if (!canShoot()) {
            return Optional.empty();
        }

        // Respect the AI's reaction time, but adjust for target priority
        if (finalTarget instanceof Player p && currentTarget == p) {
            long reactionTimeNeeded = calculateReactionTimeForTarget(currentTarget);
            if (System.currentTimeMillis() - timeTargetAcquired < reactionTimeNeeded) {
                return Optional.empty(); // Still "reacting", don't shoot yet.
            }
        }

        // All checks passed, create a shoot action with calculated inaccuracy.
        return Optional.of(shootWithInaccuracy(directionToTarget));
    }

    /**
     * Calculates the primary steering force based on the AI's current state.
     */
    private Vector2D calculateObjectiveForce(List<Obstacle> obstacles) {
        return switch (currentState) {
            case ATTACKING -> calculateAttackForce(obstacles);
            case FLEEING -> calculateFleeForce(currentTarget.position(), obstacles);
            case CAPTURING_OBJECTIVE -> calculateSeekForce(this.objectiveTargetPoint, obstacles);
            default -> calculateWanderForce(obstacles);
        };
    }

    // --- Steering Force Calculation Methods ---

    /**
     * Calculates a force to seek a target, strafing or advancing as needed.
     */
    private Vector2D calculateAttackForce(List<Obstacle> obstacles) {
        if (currentTarget == null) {
            return calculateWanderForce(obstacles); // Fallback if target is lost
        }

        // Decide whether to strafe or advance.
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStrafeTime > this.strafeInterval) {
            lastStrafeTime = currentTime;
            strafeRight = ThreadLocalRandom.current().nextBoolean();
        }

        // The AI's preferred engagement distance, with some randomness.
        // We square it once to avoid using Math.sqrt() in a loop.
        double idealRange = getWeapon().getBulletRange() - rangeSlew;
        double idealRangeSq = idealRange * idealRange;

        // If we are reloading OR we are already within our ideal engagement range,
        // prioritize strafing to be evasive.
        if (isReloading() || position().distanceSquared(currentTarget.position()) < idealRangeSq) {
            Vector2D toTarget = currentTarget.position().subtract(position());
            // Get a perpendicular vector for strafing.
            Vector2D strafeDirection = strafeRight ? new Vector2D(toTarget.y(), -toTarget.x()) : new Vector2D(-toTarget.y(), toTarget.x());
            return calculateSteerForce(strafeDirection.normalize(), obstacles);
        } else {
            // Otherwise, we are out of range and not reloading, so advance on the target.
            return calculateSeekForce(currentTarget.position(), obstacles);
        }
    }

    /**
     * Calculates a steering force to move towards a target position.
     */
    private Vector2D calculateSeekForce(Vector2D target, List<Obstacle> obstacles) {
        if (target == null) {
            return calculateWanderForce(obstacles);
        }
        Vector2D desiredDirection = target.subtract(position()).normalize();
        return calculateSteerForce(desiredDirection, obstacles);
    }

    /**
     * Calculates a steering force to move away from a target position.
     */
    private Vector2D calculateFleeForce(Vector2D target, List<Obstacle> obstacles) {
        if (target == null) {
            return calculateWanderForce(obstacles);
        }
        Vector2D desiredDirection = position().subtract(target).normalize();
        return calculateSteerForce(desiredDirection, obstacles);
    }

    /**
     * Calculates a steering force for wandering behavior.
     */
    private Vector2D calculateWanderForce(List<Obstacle> obstacles) {
        // If we are wandering towards a general objective area.
        if (objectiveTargetPoint != null) {
            long currentTime = System.currentTimeMillis();
            if (wanderTarget == null || currentTime - lastWanderDirectionChangeTime > WANDER_DIRECTION_CHANGE_INTERVAL) {
                double wanderRadius = 100.0; // Reduced from 150 to spread out more
                double randomAngle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                wanderTarget = objectiveTargetPoint.add(new Vector2D(Math.cos(randomAngle), Math.sin(randomAngle)).multiply(wanderRadius));
                lastWanderDirectionChangeTime = currentTime;
            }
        }
        // If we are just wandering randomly, use improved distribution.
        else if (wanderTarget == null || position().distanceSquared(wanderTarget) < 100 * 100) {
            wanderTarget = generateDistributedWanderTarget();
        }
        return calculateSeekForce(wanderTarget, obstacles);
    }

    /**
     * Core steering calculation. It takes a desired direction, avoids obstacles,
     * and returns the force needed to steer the AI.
     */
    private Vector2D calculateSteerForce(Vector2D desiredDirection, List<Obstacle> obstacles) {
        // Use obstacle avoidance to find a clear path.
        Vector2D clearDirection = findClearPath(desiredDirection, obstacles);
        Vector2D desiredVelocity = clearDirection.multiply(getSpeed());

        // The steering force is the difference between desired and current velocity.
        Vector2D steer = desiredVelocity.subtract(getVelocity());
        return steer.limit(AI_MAX_FORCE);
    }

    // --- Helper Methods for AI Logic ---

    /**
     * Applies a steering force to the AI's acceleration, with an optional weight.
     */
    private void applyForce(Vector2D force, double weight) {
        this.acceleration = this.acceleration.add(force.multiply(weight));
    }

    /**
     * Sets the player's mouse coordinates to aim in a specific direction.
     */
    protected void aimInDirection(Vector2D direction) {
        if (direction.magnitudeSq() == 0) return;
        Vector2D normalized = direction.normalize();
        setMouseX(position().x() + normalized.x() * 100);
        setMouseY(position().y() + normalized.y() * 100);
    }

    /**
     * Creates a {@link ShootAction} with randomized inaccuracy.
     */
    private ShootAction shootWithInaccuracy(Vector2D perfectDirection) {
        double perfectAngle = Math.atan2(perfectDirection.y(), perfectDirection.x());
        double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * this.aimInaccuracyRadians;
        double finalAngle = perfectAngle + inaccuracy;
        return new ShootAction(Math.cos(finalAngle), Math.sin(finalAngle));
    }

    /**
     * Finds the best overall target, considering both players and turrets.
     */
    private Optional<Targetable> findBestTarget(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        Targetable bestTarget = findBestShootingTarget(playerGrid, gameState.obstacles());
        return Optional.ofNullable(bestTarget);
    }

    /**
     * Finds the best enemy player to shoot at, prioritizing high-value targets over pure distance.
     */
    private Targetable findBestShootingTarget(SpatialGrid<Targetable> playerGrid, List<Obstacle> obstacles) {
        Targetable bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        double attackRange = getWeapon().getBulletRange();
        double attackRangeSq = attackRange * attackRange;

        Set<Targetable> nearTargets = playerGrid.getNearby(getX() - attackRange, getY() - attackRange, attackRange * 2, attackRange * 2);

        for (Targetable potentialTarget : nearTargets) {
            if (potentialTarget instanceof Player player) {
                if (player.getId() == this.getId() || player.isDead() || player.getTeam() == this.getTeam()) {
                    continue;
                }

                double distanceSq = this.position().distanceSquared(player.position());
                if (distanceSq < attackRangeSq) {
                    if (findBlockingObstacle(this.position(), player.position(), obstacles) == null) {
                        // Calculate priority score (lower = better)
                        double score = calculateTargetPriorityScore(player, distanceSq);
                        if (score < bestScore) {
                            bestScore = score;
                            bestTarget = player;
                        }
                    }
                }
            } else if (potentialTarget instanceof Turret turret) {
                if (turret.getTeam() == this.getTeam()) {
                    continue; // Don't shoot friendly turrets
                }

                Vector2D turretCenter = turret.position();
                double turretScore = Math.sqrt(position().distanceSquared(turretCenter));
                if (turretScore < bestScore) {
                    if (findBlockingObstacle(this.position(), turretCenter, obstacles) == null) {
                        // Simple distance-based priority for now.
                        bestScore = turretScore;
                        bestTarget = potentialTarget;
                    }
                }
            } else {
                throw new UnsupportedOperationException("fix for other targetables");
            }
        }
        return bestTarget;
    }

    /**
     * Calculates a priority score for a potential target.
     * Lower scores indicate higher priority targets.
     */
    private double calculateTargetPriorityScore(Player target, double distanceSq) {
        double baseScore = Math.sqrt(distanceSq); // Base score is distance

        // High priority: Current primary target (maintain focus)
        if (target == this.currentTarget) {
            baseScore *= 0.3; // Very high priority
        }

        // High priority: Low health enemies (easy kills)
        double healthRatio = target.getHp() / target.getMaxHp();
        if (healthRatio < 0.3) {
            baseScore *= 0.5;
        }

        // Medium priority: Powered-up enemies (threats)
        long currentTime = System.currentTimeMillis();
        if (target.getDamageBoostEndTime() > currentTime || target.getArmorUpEndTime() > currentTime) {
            baseScore *= 0.7;
        }

        // Lower priority: Reloading enemies (less immediate threat)
        if (target.isReloading()) {
            baseScore *= 1.3;
        }

        return baseScore;
    }

    /**
     * Calculates the reaction time needed for a specific target.
     * High-priority targets get faster reaction times.
     */
    private long calculateReactionTimeForTarget(Player target) {
        long baseReactionTime = this.reactionTimeMs;

        // High priority targets get much faster reactions
        double healthRatio = target.getHp() / target.getMaxHp();
        if (healthRatio < 0.3) {
            baseReactionTime = (long) (baseReactionTime * 0.4); // 60% faster for low-health targets
        }

        // Powered-up enemies are dangerous, react faster
        long currentTime = System.currentTimeMillis();
        if (target.getDamageBoostEndTime() > currentTime) {
            baseReactionTime = (long) (baseReactionTime * 0.5); // 50% faster for damage-boosted enemies
        }

        // Archetype-based reaction adjustments
        switch (this.archetype) {
            case WARRIOR:
                baseReactionTime = (long) (baseReactionTime * 0.7); // Warriors are more aggressive
                break;
            case GUARDIAN:
                baseReactionTime = (long) (baseReactionTime * 1.1); // Guardians are more cautious
                break;
            case OBJECTIVE_HOUND:
                baseReactionTime = (long) (baseReactionTime * 0.9); // Slightly faster, focused
                break;
            case BALANCED:
            default:
                // BALANCED uses default timing (no modification)
                break;
        }

        return Math.max(50, baseReactionTime); // Never go below 50ms
    }

    /**
     * Checks if a line-of-sight is blocked by an obstacle.
     */
    private Obstacle findBlockingObstacle(Vector2D start, Vector2D end, List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (obstacle == null) {
                continue;
            }
            // Broad Phase: Check if the "feeler" line segment intersects the obstacle's bounding circle.
            // If not, we can skip the expensive polygon check.
            if (!CollisionUtils.checkLineCircleCollision(start, end, obstacle.getCenter(), obstacle.getBoundingRadius())) {
                continue;
            }

            // Narrow Phase: The feeler is close, so now do the precise check.
            if (CollisionUtils.checkLinePolygonCollision(start, end, obstacle.vertices())) {
                return obstacle;
            }
        }
        return null;
    }

    /**
     * Implements "wall sliding" by projecting the desired movement direction away from an obstacle's normal.
     */
    private Vector2D findClearPath(Vector2D desiredDirection, List<Obstacle> obstacles) {
        double feelerLength = 15.0 + (getSpeed() * 5); // Dynamic feeler based on speed
        Vector2D feelerEnd = position().add(desiredDirection.multiply(feelerLength));
        Obstacle blockingObstacle = findBlockingObstacle(position(), feelerEnd, obstacles);

        if (blockingObstacle == null) {
            return desiredDirection; // Path is clear.
        }

        // Path is blocked, so we need to slide.
        Vector2D closestPointOnObstacle = findClosestPointOnObstacle(position(), blockingObstacle);
        Vector2D avoidanceDirection = position().subtract(closestPointOnObstacle).normalize();
        double projection = desiredDirection.dot(avoidanceDirection);
        Vector2D slideDirection = desiredDirection.subtract(avoidanceDirection.multiply(projection));

        return slideDirection.normalize();
    }

    /**
     * Finds the point on an obstacle's perimeter that is closest to a given point.
     */
    private Vector2D findClosestPointOnObstacle(Vector2D point, Obstacle obstacle) {
        Vector2D closestPoint = null;
        double minDistanceSq = Double.MAX_VALUE;
        List<Vector2D> vertices = obstacle.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            Vector2D p1 = vertices.get(i);
            Vector2D p2 = vertices.get((i + 1) % vertices.size());

            Vector2D closestPointOnSegment = getClosestPointOnLineSegment(point, p1, p2);
            double distanceSq = point.distanceSquared(closestPointOnSegment);

            if (distanceSq < minDistanceSq) {
                minDistanceSq = distanceSq;
                closestPoint = closestPointOnSegment;
            }
        }
        return closestPoint;
    }

    /**
     * Calculates the closest point on a line segment to a given point.
     * This is a core utility for collision avoidance.
     */
    private Vector2D getClosestPointOnLineSegment(Vector2D p, Vector2D a, Vector2D b) {
        Vector2D ab = b.subtract(a);
        double ab2 = ab.magnitudeSq();
        if (ab2 < 1e-9) { // Treat very short segments as a single point to avoid NaN.
            return a;
        }
        Vector2D ap = p.subtract(a);
        double t = ap.dot(ab) / ab2;
        t = Math.max(0, Math.min(1, t)); // Clamp t to the range [0, 1]
        return a.add(ab.multiply(t));
    }

    // --- High-Level Steering Behaviors ---

    /**
     * Calculates a steering force to flee from dangerous hazards.
     */
    private Vector2D calculateHazardAvoidanceForce(List<FieldEffect> fieldEffects) {
        Vector2D totalAvoidanceForce = Vector2D.ZERO;
        if (fieldEffects == null) return totalAvoidanceForce;

        for (FieldEffect fieldEffect : fieldEffects) {
            double awarenessRadius = fieldEffect.getRadius() + 20;
            if (getTeam() != fieldEffect.getTeam()
                    && position().distanceSquared(fieldEffect.position()) < awarenessRadius * awarenessRadius) {
                Vector2D fleeDirection = position().subtract(fieldEffect.position());
                double weight = switch (fieldEffect.getType()) {
                    case MINE -> 0.3;
                    case POISON -> 0.25;
                    default -> 0.1;
                };
                totalAvoidanceForce = totalAvoidanceForce.add(fleeDirection.normalize().multiply(weight));
            }
        }
        return totalAvoidanceForce;
    }

    /**
     * Calculates a strong, short-range repulsive force to prevent getting stuck on walls.
     */
    private Vector2D calculateObstacleSeparationForce(List<Obstacle> obstacles) {
        Vector2D totalSeparationForce = Vector2D.ZERO;
        double separationRadius = 30.0; // Increased for more "personal space" to reduce wall jitter

        for (Obstacle obstacle : obstacles) {
            // Broad Phase: Check if the AI's "personal space" bubble overlaps the obstacle's bounding circle.
            double combinedRadius = separationRadius + obstacle.getBoundingRadius();
            if (position().distanceSquared(obstacle.getCenter()) > combinedRadius * combinedRadius) {
                continue; // Not close enough to worry about.
            }

            // Narrow Phase: We are close, so find the exact closest point to push away from.
            Vector2D closestPoint = findClosestPointOnObstacle(position(), obstacle);
            double distanceSq = position().distanceSquared(closestPoint);
            if (distanceSq < separationRadius * separationRadius) {
                Vector2D fleeDirection = position().subtract(closestPoint);
                double strength = 1.0 - (Math.sqrt(distanceSq) / separationRadius);
                totalSeparationForce = totalSeparationForce.add(fleeDirection.normalize().multiply(strength));
            }
        }
        return totalSeparationForce;
    }

    /**
     * Calculates a steering force based on nearby power-ups and powered-up enemies.
     */
    private Vector2D calculatePowerUpInfluenceForce(GameState gameState) {
        Vector2D totalInfluenceForce = Vector2D.ZERO;
        long currentTime = System.currentTimeMillis();

        // Avoid powered-up enemies
        for (Player player : gameState.players()) {
            if (player.getTeam() == this.getTeam() || player.isDead()) {
                continue;
            }
            boolean isThreat = player.getArmorUpEndTime() > currentTime || player.getDamageBoostEndTime() > currentTime;
            if (isThreat && position().distanceSquared(player.position()) < 400 * 400) {
                totalInfluenceForce = totalInfluenceForce.add(position().subtract(player.position()).normalize().multiply(1.0));
            }
        }

        // Seek valuable power-ups
        if (gameState.powerUps() != null) {
            for (PowerUp powerUp : gameState.powerUps()) {
                if (position().distanceSquared(powerUp.getPosition()) < 500 * 500) {
                    double weight = 0.5; // Default attraction
                    if (powerUp.getType() == PowerUpType.HEALTH_PACK) {
                        weight = 1.5 * (1.0 - (getHp() / getMaxHp()));
                    } else if (powerUp.getType() == PowerUpType.ARMOR_UP || powerUp.getType() == PowerUpType.DAMAGE_BOOST) {
                        weight = 1.0;
                    }
                    if (weight > 0.1) {
                        totalInfluenceForce = totalInfluenceForce.add(powerUp.getPosition().subtract(position()).normalize().multiply(weight));
                    }
                }
            }
        }
        return totalInfluenceForce;
    }

    // --- State Setters for Strategy ---

    public void setCurrentState(AIState state) {
        if (this.currentState != state) {
            this.stateChangeTime = System.currentTimeMillis();

            // Clear objective points when entering pure wandering to prevent clustering
            if (state == AIState.WANDERING && this.currentState != AIState.WANDERING) {
                this.objectiveTargetPoint = null;
                this.wanderTarget = null; // Force new wander target selection
            }
        }
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

    /**
     * Generates a distributed wander target that avoids clustering in the center.
     * Uses team preference and avoids recently visited areas.
     */
    private Vector2D generateDistributedWanderTarget() {
        // Prefer areas closer to team's side of the map to encourage territorial behavior
        double teamBias = getTeam() == 1 ? 0.3 : 0.7; // Team 1 left, Team 2 right
        double variance = 0.4; // Allow some exploration to other areas

        // Generate x coordinate with team bias but allow cross-map movement
        double xBias = teamBias + (ThreadLocalRandom.current().nextGaussian() * variance);
        xBias = Math.max(0.1, Math.min(0.9, xBias)); // Clamp to reasonable bounds
        double x = Config.GAME_WIDTH * xBias;

        // Generate y coordinate more randomly to encourage vertical movement
        double y = ThreadLocalRandom.current().nextDouble(50, Config.GAME_HEIGHT - 50);

        // Add some avoidance of center area when no specific objective
        double centerX = Config.GAME_WIDTH / 2.0;
        double centerY = Config.GAME_HEIGHT / 2.0;
        double distFromCenter = Math.sqrt((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY));

        // If too close to center, push away slightly
        if (distFromCenter < 100) {
            double pushAngle = Math.atan2(y - centerY, x - centerX);
            x = centerX + Math.cos(pushAngle) * 120;
            y = centerY + Math.sin(pushAngle) * 120;
        }

        // Ensure we stay within map bounds
        x = Math.max(50, Math.min(Config.GAME_WIDTH - 50, x));
        y = Math.max(50, Math.min(Config.GAME_HEIGHT - 50, y));

        return new Vector2D(x, y);
    }
}
