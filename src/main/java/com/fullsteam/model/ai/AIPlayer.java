package com.fullsteam.model.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.SpatialGrid;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Base;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameState;
import com.fullsteam.model.GridPoint;
import com.fullsteam.model.MountedWeapon;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.PowerUpType;
import com.fullsteam.model.RandomNames;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.Collection;
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
        CAPTURING_OBJECTIVE,
        SEEKING_VEHICLE
    }

    // --- AI State & Strategy ---
    @JsonIgnore
    private transient AIState currentState = AIState.WANDERING;
    @JsonIgnore
    protected transient Player currentTarget;
    @JsonIgnore
    private transient Vector2D objectiveTargetPoint;
    @JsonIgnore
    private transient Vector2D wanderTarget;
    @JsonIgnore
    private transient Vehicle targetVehicle;
    @JsonIgnore
    private transient Long lastDriverSeenTime;
    @JsonIgnore
    private transient final IAIStrategy aiStrategy;
    @JsonIgnore
    private transient final AIArchetype archetype;

    // --- Debug/Analytics Fields ---
    @JsonIgnore
    private transient long stateChangeTime = System.currentTimeMillis();

    // --- AI Behavior Timing & State ---
    @JsonIgnore
    private transient long lastWanderDirectionChangeTime;
    @JsonIgnore
    private transient long lastStrafeTime;
    @JsonIgnore
    private transient boolean strafeRight = true;

    // --- Steering Behavior Fields ---
    @JsonIgnore
    private transient Vector2D acceleration;

    // --- AI "Personality" Traits ---
    @JsonIgnore
    private transient final double aimInaccuracyRadians;
    @JsonIgnore
    private transient final long strafeInterval;
    @JsonIgnore
    private transient final double rangeSlew;

    // --- Input Smoothing Fields ---
    @JsonIgnore
    private transient PlayerInput previousInput;
    @JsonIgnore
    private transient final double inputSmoothingFactor; // 0.0 = no smoothing, 1.0 = maximum smoothing

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
        this.aimInaccuracyRadians = Math.max(0.01, BASE_AIM_INACCURACY_RADIANS + (ThreadLocalRandom.current().nextDouble() - 0.4) * 0.04);
        this.strafeInterval = BASE_STRAFE_INTERVAL_MS + (long) (ThreadLocalRandom.current().nextGaussian() * 300);
        this.rangeSlew = ThreadLocalRandom.current().nextGaussian() * 50;
        this.acceleration = Vector2D.ZERO;

        // Initialize input smoothing - different archetypes have different smoothing levels
        this.inputSmoothingFactor = calculateSmoothingFactor(archetype);
        this.previousInput = new PlayerInput(); // Start with empty input
    }

    /**
     * Calculates input smoothing factor based on AI archetype.
     * More tactical archetypes get more smoothing, aggressive ones get less.
     */
    private double calculateSmoothingFactor(AIArchetype archetype) {
        return switch (archetype) {
            case WARRIOR -> 0.6; // Very responsive, minimal smoothing
            case GUARDIAN -> 0.9; // More measured, smooth movements
            case OBJECTIVE_HOUND -> 0.7; // Focused but responsive
            case BALANCED -> 0.75; // Moderate smoothing
        };
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
     * 4. Movement input is calculated based on desired velocity.
     * 5. Shooting, reloading, and action decisions are made independently.
     *
     * @param gameState  The current game mode's state information.
     * @param playerGrid The spatial grid for proximity queries.
     * @param delta      Time delta for physics calculations.
     * @return A PlayerInput object containing all AI decisions for this frame.
     */
    public PlayerInput generateInput(GameState gameState, SpatialGrid<Targetable> playerGrid, long delta) {
        PlayerInput input = new PlayerInput();

        if (isDead()) {
            return input; // Return empty input for dead AI
        }

        // Handle vehicle-related logic first
        handleVehicleLogic(gameState);

        // If in a vehicle, generate vehicle-specific input
        if (getVehicleId() != null) {
            PlayerInput vehicleInput = generateVehicleInput(gameState, playerGrid, input);
            // Apply smoothing to vehicle input as well
            PlayerInput smoothedInput = applySmoothingToInput(vehicleInput);
            // Store current input as previous for next frame
            this.previousInput = copyInput(smoothedInput);
            return smoothedInput;
        }

        // Standard ground-based AI logic
        // 1. Reset acceleration for the new frame.
        this.acceleration = Vector2D.ZERO;

        // 2. Calculate and apply all steering forces, from highest to lowest priority.
        // The order is crucial for realistic behavior.
        applySteeringForces(gameState);

        // 3. Calculate movement input based on desired velocity
        generateMovementInput(input, delta);

        // 4. Handle aiming, shooting, reloading, and action decisions
        generateCombatInput(gameState, playerGrid, input);

        // 5. Apply input smoothing to reduce jittery behavior
        PlayerInput smoothedInput = applySmoothingToInput(input);

        // Store current input as previous for next frame
        this.previousInput = copyInput(smoothedInput);

        return smoothedInput;
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
     * Generates movement input based on the AI's calculated desired velocity.
     */
    private void generateMovementInput(PlayerInput input, long delta) {
        // Calculate desired velocity based on steering forces
        Vector2D desiredVelocity = getVelocity().add(this.acceleration);
        if (desiredVelocity.magnitudeSq() > 0.01) {
            Vector2D direction = desiredVelocity.normalize();
            input.setMoveX(direction.x());
            input.setMoveY(direction.y());
        } else {
            // If no movement desired, moveX and moveY remain 0 (default)
            input.setMoveX(0);
            input.setMoveY(0);
        }
    }

    /**
     * Generates combat-related input (shooting, reloading, aiming).
     */
    private void generateCombatInput(GameState gameState, SpatialGrid<Targetable> playerGrid, PlayerInput input) {
        Optional<Targetable> bestTargetInfo = findBestTarget(gameState, playerGrid);

        if (bestTargetInfo.isEmpty()) {
            // If no target, but we are moving, aim in the direction of movement.
            if (getVelocity().magnitudeSq() > 0.01) {
                aimInDirection(getVelocity());
                input.setMouseX(getMouseX());
                input.setMouseY(getMouseY());
            }

            // Check if we need to reload when no targets
            if (getAmmoInMag() <= 0 && !isReloading()) {
                input.setReload(true);
            }
            return;
        }

        Targetable finalTarget = bestTargetInfo.get();
        Vector2D directionToTarget = finalTarget.position().subtract(position());

        // Check if we are able to fire (not reloading, cooldown is over, etc.).
        if (!canShoot()) {
            // Still aim perfectly at target when not shooting (tracking)
            aimInDirection(directionToTarget);
            input.setMouseX(getMouseX());
            input.setMouseY(getMouseY());

            // Try to reload if we're out of ammo
            if (getAmmoInMag() <= 0 && !isReloading()) {
                input.setReload(true);
            }
            return;
        }
        // All checks passed, fire with inaccuracy!
        aimInDirectionWithInaccuracy(directionToTarget);
        input.setMouseX(getMouseX());
        input.setMouseY(getMouseY());

        // different archetypes have different "aim" or "sustain fire" thresholds
        double firingThreshold = switch (archetype) {
            case BALANCED -> .65; // mostly fire when ready
            case WARRIOR -> .9; // always ready to fire
            case GUARDIAN -> .6; // conservative
            case OBJECTIVE_HOUND -> .5; // focused on other tasks
        };
        input.setFire(ThreadLocalRandom.current().nextDouble() < firingThreshold);
    }

    /**
     * Generates vehicle-specific input when the AI is in a vehicle.
     */
    private PlayerInput generateVehicleInput(GameState gameState, SpatialGrid<Targetable> playerGrid, PlayerInput input) {
        Vehicle currentVehicle = getCurrentVehicle(gameState);
        if (currentVehicle == null) {
            return input;
        }

        // Update AI strategy even when in vehicles to maintain target tracking
        if ((System.currentTimeMillis() - stateChangeTime) > AI_DECISION_COOLDOWN_MS) {
            aiStrategy.updateAIState(this, gameState);
        }

        // Find the weapon this AI is controlling
        MountedWeapon controlledWeapon = currentVehicle.getWeaponControlledBy(getId());

        // Generate shooting input if we have a weapon
        if (controlledWeapon != null) {
            generateVehicleWeaponInput(gameState, playerGrid, controlledWeapon, currentVehicle, input);
        }

        return input;
    }

    /**
     * Generates weapon firing input for mounted weapons.
     */
    private void generateVehicleWeaponInput(GameState gameState, SpatialGrid<Targetable> playerGrid,
                                            MountedWeapon weapon, Vehicle vehicle, PlayerInput input) {

        // Handle weapon reloading if needed
        if (!weapon.canShoot()) {
            // If we can't shoot but have a current target, still aim at them
            if (currentTarget != null && !currentTarget.isDead()) {
                aimAtTargetWithMountedWeapon(weapon, currentTarget, vehicle);
                input.setMouseX(getMouseX());
                input.setMouseY(getMouseY());
            }

            // Try to reload if out of ammo
            if (weapon.getCurrentAmmo() <= 0 && !weapon.isReloading()) {
                input.setReload(true);
            }
            return;
        }

        // Find the best target for the mounted weapon
        Optional<Targetable> bestTarget = findBestMountedWeaponTarget(gameState, playerGrid, weapon, vehicle);

        if (bestTarget.isEmpty()) {
            return;
        }

        Targetable target = bestTarget.get();

        // Update current target for consistency with ground-based AI
        if (target instanceof Player player && !player.isDead()) {
            setCurrentTarget(player);
        }

        Vector2D directionToTarget = target.position().subtract(weapon.position());

        // Calculate desired weapon angle
        double desiredAngle = Math.atan2(directionToTarget.y(), directionToTarget.x());

        // Check if target is within weapon traverse limits
        double constrainedAngle = weapon.getConstrainedAngle(desiredAngle, vehicle.getAngle());
        double angleDifference = Math.abs(desiredAngle - constrainedAngle);

        // More lenient tolerance for aiming precision to reduce target loss
        if (angleDifference < 0.2) { // ~11.5 degrees tolerance
            // Apply inaccuracy when actually firing
            aimAtTargetWithMountedWeaponInaccuracy(weapon, target, vehicle);
            input.setMouseX(getMouseX());
            input.setMouseY(getMouseY());
            input.setFire(true);
        } else {
            // Just track the target without firing (perfect aim for tracking)
            aimAtTargetWithMountedWeapon(weapon, target, vehicle);
            input.setMouseX(getMouseX());
            input.setMouseY(getMouseY());
        }
    }

    /**
     * Applies smoothing/interpolation to input to reduce jittery AI behavior.
     * Uses exponential smoothing: newValue = (1-factor) * currentValue + factor * previousValue
     */
    private PlayerInput applySmoothingToInput(PlayerInput currentInput) {
        if (previousInput == null || inputSmoothingFactor <= 0.0) {
            return currentInput; // No smoothing if no previous input or factor is 0
        }

        PlayerInput smoothedInput = new PlayerInput();

        // Smooth movement input (most important for reducing jittery movement)
        double currentFactor = 1.0 - inputSmoothingFactor;
        double previousFactor = inputSmoothingFactor;

        smoothedInput.setMoveX(currentFactor * currentInput.getMoveX() + previousFactor * previousInput.getMoveX());
        smoothedInput.setMoveY(currentFactor * currentInput.getMoveY() + previousFactor * previousInput.getMoveY());

        // Mouse smoothing - reduced for vehicle weapons to maintain precision
        double mouseSmoothingFactor;
        if (getVehicleId() != null) {
            // Minimal mouse smoothing for vehicle weapons - traverse constraints provide natural smoothing
            mouseSmoothingFactor = inputSmoothingFactor * 0.1;
        } else {
            // Normal mouse smoothing for ground-based AI
            mouseSmoothingFactor = inputSmoothingFactor * 0.3;
        }

        double mouseCurrentFactor = 1.0 - mouseSmoothingFactor;

        smoothedInput.setMouseX(mouseCurrentFactor * currentInput.getMouseX() + mouseSmoothingFactor * previousInput.getMouseX());
        smoothedInput.setMouseY(mouseCurrentFactor * currentInput.getMouseY() + mouseSmoothingFactor * previousInput.getMouseY());

        // Don't smooth boolean actions - they should be immediate
        smoothedInput.setFire(currentInput.isFire());
        smoothedInput.setReload(currentInput.isReload());
        smoothedInput.setAction1(currentInput.isAction1());
        smoothedInput.setAction2(currentInput.isAction2());
        smoothedInput.setAltFire(currentInput.isAltFire());

        return smoothedInput;
    }

    /**
     * Creates a deep copy of a PlayerInput object.
     */
    private PlayerInput copyInput(PlayerInput input) {
        PlayerInput copy = new PlayerInput();
        copy.setMoveX(input.getMoveX());
        copy.setMoveY(input.getMoveY());
        copy.setMouseX(input.getMouseX());
        copy.setMouseY(input.getMouseY());
        copy.setFire(input.isFire());
        copy.setReload(input.isReload());
        copy.setAction1(input.isAction1());
        copy.setAction2(input.isAction2());
        copy.setAltFire(input.isAltFire());
        return copy;
    }


    /**
     * Calculates the primary steering force based on the AI's current state.
     */
    private Vector2D calculateObjectiveForce(List<Obstacle> obstacles) {
        return switch (currentState) {
            case ATTACKING -> calculateAttackForce(obstacles);
            case FLEEING -> calculateFleeForce(currentTarget.position(), obstacles);
            case CAPTURING_OBJECTIVE -> calculateSeekForce(this.objectiveTargetPoint, obstacles);
            case SEEKING_VEHICLE -> calculateVehicleSeekForce(obstacles);
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
        if (direction.magnitudeSq() == 0) {
            return;
        }
        Vector2D normalized = direction.normalize();
        setMouseX(position().x() + normalized.x() * 100);
        setMouseY(position().y() + normalized.y() * 100);
    }

    /**
     * Sets the player's mouse coordinates to aim in a specific direction with AI inaccuracy applied.
     * This should be used when the AI is actually firing to simulate realistic aiming.
     */
    protected void aimInDirectionWithInaccuracy(Vector2D direction) {
        if (direction.magnitudeSq() == 0) {
            return;
        }

        // Calculate perfect aim angle
        double perfectAngle = Math.atan2(direction.y(), direction.x());

        // Apply AI-specific inaccuracy
        double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * this.aimInaccuracyRadians;
        double finalAngle = perfectAngle + inaccuracy;

        // Convert back to mouse coordinates
        Vector2D aimDirection = new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle));
        setMouseX(position().x() + aimDirection.x() * 100);
        setMouseY(position().y() + aimDirection.y() * 100);
    }


    /**
     * Finds the best overall target, considering both players and turrets.
     */
    private Optional<Targetable> findBestTarget(GameState gameState, SpatialGrid<Targetable> playerGrid) {
        if (isVisionObscured()) {
            return Optional.empty();
        }
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
            switch (potentialTarget) {
                case Player player -> {
                    if (player.getId() == this.getId() || player.isDead() || player.getTeam() == this.getTeam() || player.getInvisibilityEndTime() > System.currentTimeMillis()) {
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
                }
                case Turret turret -> {
                    if (turret.getTeam() != this.getTeam()) {
                        Vector2D turretCenter = turret.position();
                        double turretScore = Math.sqrt(position().distanceSquared(turretCenter));
                        if (turretScore < bestScore) {
                            if (findBlockingObstacle(this.position(), turretCenter, obstacles) == null) {
                                // Simple distance-based priority for now.
                                bestScore = turretScore;
                                bestTarget = potentialTarget;
                            }
                        }
                    }
                }
                case GridPoint gridPoint -> {
                    if (gridPoint.getTeam() != this.getTeam()) {
                        Vector2D turretCenter = gridPoint.position();
                        double turretScore = Math.sqrt(position().distanceSquared(turretCenter));
                        if (turretScore < bestScore) {
                            if (findBlockingObstacle(this.position(), turretCenter, obstacles) == null) {
                                // Simple distance-based priority for now.
                                bestScore = turretScore;
                                bestTarget = potentialTarget;
                            }
                        }
                    }
                }
                case Base base -> {
                    if (base.getTeam() != getTeam()) {
                        Vector2D turretCenter = base.position();
                        double turretScore = Math.sqrt(position().distanceSquared(turretCenter));
                        if (turretScore < bestScore) {
                            if (findBlockingObstacle(this.position(), turretCenter, obstacles) == null) {
                                // Simple distance-based priority for now.
                                bestScore = turretScore;
                                bestTarget = potentialTarget;
                            }
                        }
                    }
                }
                case Vehicle vehicle -> {
                    if (vehicle.getDriverId() != null && vehicle.getTeam() != getTeam()) {
                        Vector2D vehicleCenter = vehicle.position();
                        double vehicleScore = Math.sqrt(position().distanceSquared(vehicleCenter));
                        if (vehicleScore < bestScore) {
                            if (findBlockingObstacle(this.position(), vehicleCenter, obstacles) == null) {
                                bestScore = switch (vehicle.getVehicleType()) {
                                    case JEEP -> vehicleScore * 1.75;
                                    case DAVINCI -> vehicleScore * 1.25;
                                    case FIXED_CANNON -> vehicleScore * 0.5;
                                    case MECH -> vehicleScore * 1.35;
                                    case TANK -> vehicleScore * 1.1;
                                };
                                bestTarget = potentialTarget;
                            }
                        }
                    }
                }
                case null, default -> throw new UnsupportedOperationException("fix for other targetables");
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
    private Vector2D calculateHazardAvoidanceForce(Collection<FieldEffect> fieldEffects) {
        Vector2D totalAvoidanceForce = Vector2D.ZERO;
        if (fieldEffects == null) {
            return totalAvoidanceForce;
        }

        for (FieldEffect fieldEffect : fieldEffects) {
            double awarenessRadius = fieldEffect.getRadius() + 20;
            if (getTeam() != fieldEffect.getTeam()
                && position().distanceSquared(fieldEffect.position()) < awarenessRadius * awarenessRadius) {
                Vector2D fleeDirection = position().subtract(fieldEffect.position());
                double weight = switch (fieldEffect.getType()) {
                    case MINE -> 0.3;
                    case POISON -> 0.25;
                    case GRAVITY_WELL -> 0.4;
                    case PORTAL -> 0; // safe to travel
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
            if (obstacle != null) {
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

    // --- Vehicle-Related AI Methods ---

    /**
     * Handles vehicle entry/exit logic and mounted weapon control.
     */
    private void handleVehicleLogic(GameState gameState) {
        Vehicle currentVehicle = getCurrentVehicle(gameState);

        if (currentVehicle != null) {
            // AI is in a vehicle - check if driver is still present
            Player driver = getVehicleDriver(currentVehicle, gameState);
            if (driver != null && !driver.isDead()) {
                // Update last driver seen time
                lastDriverSeenTime = System.currentTimeMillis();
            } else {
                // Driver is missing or dead
                if (lastDriverSeenTime == null) {
                    // First time noticing driver is gone - initialize the timer
                    lastDriverSeenTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastDriverSeenTime > 3000) { // 3 seconds grace period
                    // Driver has been gone too long, exit vehicle
                    setCurrentState(AIState.WANDERING);
                    this.targetVehicle = null;
                    // Note: Actual vehicle exit is handled by the VehicleManager
                }
            }
        } else {
            // AI is not in a vehicle - consider entering one
            considerVehicleEntry(gameState);
            // Reset driver timer when not in vehicle
            lastDriverSeenTime = null;
        }
    }

    /**
     * Considers whether the AI should seek a vehicle to enter.
     */
    private void considerVehicleEntry(GameState gameState) {
        if (currentState == AIState.SEEKING_VEHICLE && targetVehicle != null) {
            // Already seeking a vehicle - check if it's still valid
            if (isVehicleAvailable(targetVehicle)) {
                double distanceToVehicle = position().distance(targetVehicle.position());
                if (distanceToVehicle <= Config.VEHICLE_INTERACTION_RADIUS) {
                    // Close enough to enter - this will be handled by the game manager
                    return;
                }
            } else {
                // Target vehicle is no longer available
                targetVehicle = null;
                setCurrentState(AIState.WANDERING);
            }
        }

        // Look for available vehicles with human drivers
        if (ThreadLocalRandom.current().nextDouble() < 0.02) { // 2% chance per frame to look for vehicles
            Vehicle bestVehicle = findBestAvailableVehicle(gameState);
            if (bestVehicle != null) {
                targetVehicle = bestVehicle;
                setCurrentState(AIState.SEEKING_VEHICLE);
            }
        }
    }

    /**
     * Finds the best available vehicle for the AI to enter.
     */
    private Vehicle findBestAvailableVehicle(GameState gameState) {
        Vehicle bestVehicle = null;
        double bestScore = Double.MAX_VALUE;
        double maxSearchDistance = 300.0; // Maximum distance to consider vehicles

        for (Vehicle vehicle : gameState.vehicles()) {
            if (!isVehicleAvailable(vehicle)) {
                continue;
            }

            // Check if vehicle has a human driver from our team
            Player driver = getVehicleDriver(vehicle, gameState);
            if (driver == null || driver instanceof AIPlayer || driver.getTeam() != getTeam()) {
                continue;
            }

            double distance = position().distance(vehicle.position());
            if (distance > maxSearchDistance) {
                continue;
            }

            // Prefer closer vehicles with better health
            double healthRatio = vehicle.getHp() / vehicle.getMaxHp();
            double score = distance / healthRatio; // Lower is better

            if (score < bestScore) {
                bestScore = score;
                bestVehicle = vehicle;
            }
        }

        return bestVehicle;
    }

    /**
     * Checks if a vehicle is available for the AI to enter.
     */
    private boolean isVehicleAvailable(Vehicle vehicle) {
        if (vehicle == null || vehicle.isDestroyed()) {
            return false;
        }

        // Check if there are available seats
        return hasAvailableSeats(vehicle) &&
               (vehicle.getTeam() < 0 || vehicle.getTeam() == getTeam());
    }

    /**
     * Checks if a vehicle has available seats.
     */
    private boolean hasAvailableSeats(Vehicle vehicle) {
        // Check if any seat is empty (we can't access seats directly, so use passenger IDs)
        return vehicle.getPassengerIds().size() < vehicle.getMaxPassengers();
    }

    /**
     * Gets the driver of a vehicle.
     */
    private Player getVehicleDriver(Vehicle vehicle, GameState gameState) {
        // Use the vehicle's getDriverId method which should correctly identify the driver
        Long driverId = vehicle.getDriverId();
        if (driverId == null) {
            return null;
        }

        // Find the player with this ID in the game state
        for (Player player : gameState.players()) {
            if (player.getId() == driverId) { // Compare primitive long with Long
                return player;
            }
        }
        return null;
    }

    /**
     * Gets the current vehicle the AI is in.
     */
    private Vehicle getCurrentVehicle(GameState gameState) {
        if (getVehicleId() == null) {
            return null;
        }

        for (Vehicle vehicle : gameState.vehicles()) {
            if (vehicle.id() == getVehicleId()) {
                return vehicle;
            }
        }
        return null;
    }

    /**
     * Calculates a steering force to seek the target vehicle.
     */
    private Vector2D calculateVehicleSeekForce(List<Obstacle> obstacles) {
        if (targetVehicle == null) {
            return calculateWanderForce(obstacles);
        }
        return calculateSeekForce(targetVehicle.position(), obstacles);
    }


    /**
     * Helper method to aim the mounted weapon at a target and update mouse position.
     */
    private void aimAtTargetWithMountedWeapon(MountedWeapon weapon, Targetable target, Vehicle vehicle) {
        Vector2D directionToTarget = target.position().subtract(weapon.position());
        double desiredAngle = Math.atan2(directionToTarget.y(), directionToTarget.x());
        double constrainedAngle = weapon.getConstrainedAngle(desiredAngle, vehicle.getAngle());

        Vector2D aimPoint = weapon.position().add(
                new Vector2D(Math.cos(constrainedAngle), Math.sin(constrainedAngle)).multiply(100)
        );
        setMouseX(aimPoint.x());
        setMouseY(aimPoint.y());

        // Update the weapon's angle based on this mouse position
        weapon.updateAngle(this.mouseX, this.mouseY, vehicle.getAngle());
    }

    /**
     * Helper method to aim the mounted weapon at a target with inaccuracy applied.
     * Applies inaccuracy BEFORE constraint to ensure the final angle stays within traverse limits.
     */
    private void aimAtTargetWithMountedWeaponInaccuracy(MountedWeapon weapon, Targetable target, Vehicle vehicle) {
        Vector2D directionToTarget = target.position().subtract(weapon.position());
        double desiredAngle = Math.atan2(directionToTarget.y(), directionToTarget.x());

        // Apply AI-specific inaccuracy to the desired angle BEFORE constraining
        double inaccuracy = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * this.aimInaccuracyRadians;
        double inaccurateDesiredAngle = desiredAngle + inaccuracy;

        // Then constrain the inaccurate angle to weapon limits
        double finalAngle = weapon.getConstrainedAngle(inaccurateDesiredAngle, vehicle.getAngle());

        Vector2D aimPoint = weapon.position().add(
                new Vector2D(Math.cos(finalAngle), Math.sin(finalAngle)).multiply(100)
        );
        setMouseX(aimPoint.x());
        setMouseY(aimPoint.y());

        // Update the weapon's angle based on this mouse position
        weapon.updateAngle(this.mouseX, this.mouseY, vehicle.getAngle());
    }

    /**
     * Finds the best target for a mounted weapon, considering traverse limits.
     */
    private Optional<Targetable> findBestMountedWeaponTarget(GameState gameState,
                                                             SpatialGrid<Targetable> playerGrid,
                                                             MountedWeapon weapon,
                                                             Vehicle vehicle) {
        Targetable bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        double weaponRange = weapon.getWeapon().getBulletRange();

        // First, check if our current target is still valid and in range
        if (currentTarget != null && !currentTarget.isDead() && isValidMountedWeaponTarget(currentTarget)) {
            double distanceSq = weapon.position().distanceSquared(currentTarget.position());
            if (distanceSq <= weaponRange * weaponRange) {
                Vector2D toTarget = currentTarget.position().subtract(weapon.position());
                double targetAngle = Math.atan2(toTarget.y(), toTarget.x());
                double constrainedAngle = weapon.getConstrainedAngle(targetAngle, vehicle.getAngle());
                double angleDifference = Math.abs(targetAngle - constrainedAngle);

                // Give significant preference to maintaining current target (target stickiness)
                if (angleDifference < 0.3 && // More lenient angle check for current target
                    findBlockingObstacle(weapon.position(), currentTarget.position(), gameState.obstacles()) == null) {
                    return Optional.of(currentTarget);
                }
            }
        }

        Set<Targetable> nearTargets = playerGrid.getNearby(weapon.position(), weapon.getWeapon().getBulletRange());
        for (Targetable potentialTarget : nearTargets) {
            if (!isValidMountedWeaponTarget(potentialTarget)) {
                continue;
            }

            double distanceSq = weapon.position().distanceSquared(potentialTarget.position());

            // Check if target is within traverse arc
            Vector2D toTarget = potentialTarget.position().subtract(weapon.position());
            double targetAngle = Math.atan2(toTarget.y(), toTarget.x());
            double constrainedAngle = weapon.getConstrainedAngle(targetAngle, vehicle.getAngle());
            double angleDifference = Math.abs(targetAngle - constrainedAngle);

            // Skip targets that are too far outside traverse limits
            if (angleDifference > 0.4) {
                continue;
            }

            // Check line of sight
            if (findBlockingObstacle(weapon.position(), potentialTarget.position(), gameState.obstacles()) != null) {
                continue;
            }

            // Calculate priority score
            double score = Math.sqrt(distanceSq) + (angleDifference * 80); // Reduced angle penalty

            if (potentialTarget instanceof Player player) {
                // High priority: Current primary target (maintain focus)
                if (player == this.currentTarget) {
                    score *= 0.3; // Very high priority for current target
                }

                // Prioritize low-health enemies
                double healthRatio = player.getHp() / player.getMaxHp();
                if (healthRatio < 0.3) {
                    score *= 0.7;
                }

                // Slightly prioritize powered-up enemies (threats)
                long currentTime = System.currentTimeMillis();
                if (player.getDamageBoostEndTime() > currentTime || player.getSpeedBoostEndTime() > currentTime) {
                    score *= 0.8;
                }
            }

            if (score < bestScore) {
                bestScore = score;
                bestTarget = potentialTarget;
            }
        }

        return Optional.ofNullable(bestTarget);
    }

    /**
     * Checks if a target is valid for mounted weapon targeting.
     */
    private boolean isValidMountedWeaponTarget(Targetable target) {
        return switch (target) {
            case Player player -> player.getId() != this.getId() &&
                                  !player.isDead() &&
                                  player.getTeam() != this.getTeam() &&
                                  player.getInvisibilityEndTime() <= System.currentTimeMillis();
            case Vehicle vehicle -> vehicle.getTeam() != this.getTeam() &&
                                    !vehicle.isDestroyed() &&
                                    vehicle.getDriverId() != null;
            case Turret turret -> turret.getTeam() != this.getTeam();
            case Base base -> base.getTeam() != this.getTeam();
            default -> false;
        };
    }

    /**
     * Returns true if the AI should attempt to enter a vehicle.
     */
    public boolean shouldEnterVehicle() {
        return getVehicleId() == null
               && currentState == AIState.SEEKING_VEHICLE
               && targetVehicle != null
               && position().distance(targetVehicle.position()) <= Config.VEHICLE_INTERACTION_RADIUS;
    }

    /**
     * Returns true if the AI should exit its current vehicle.
     */
    public boolean shouldExitVehicle(GameState gameState) {
        if (getVehicleId() == null) {
            return false;
        }
        Vehicle currentVehicle = getCurrentVehicle(gameState);
        if (currentVehicle == null) {
            return false;
        }

        // Don't exit immediately after entering - wait for handleVehicleLogic to set up timing
        if (lastDriverSeenTime == null) {
            return false; // Just entered vehicle, don't exit yet
        }

        // Exit if driver has been gone too long
        Player driver = getVehicleDriver(currentVehicle, gameState);
        if (driver == null || driver.isDead()) {
            return System.currentTimeMillis() - lastDriverSeenTime > 3000;
        }

        return false;
    }
}
