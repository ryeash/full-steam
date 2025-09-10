package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.SpatialGrid;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.GridPoint;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;
import static com.fullsteam.Config.PLAYER_RADIUS;
import static com.fullsteam.Config.PLAYER_SIZE;

/**
 * Handles all physics-related operations including collision detection, movement validation,
 * and spatial grid management. Separated from AbstractGameStateManager to follow
 * Single Responsibility Principle.
 */
public class PhysicsEngine {
    private final GameEntities entities;

    public PhysicsEngine(GameEntities entities) {
        this.entities = entities;
    }

    /**
     * Populates the spatial grid with all targetable entities for efficient collision detection
     */
    public void populateSpatialGrids() {
        SpatialGrid<Targetable> targetGrid = entities.getTargetGrid();
        targetGrid.clear();
        for (PlayerSession playerSession : entities.getPlayerSessions().values()) {
            Player player = playerSession.getPlayer();
            if (player.getVehicleId() != null) {
                continue;
            }
            targetGrid.insert(player, player.getX() - PLAYER_RADIUS, player.getY() - PLAYER_RADIUS, PLAYER_SIZE, PLAYER_SIZE);
        }
        for (FieldEffect fieldEffect : entities.getFieldEffects().values()) {
            if (fieldEffect instanceof GridPoint gridPoint) {
                double size = gridPoint.getRadius() * 2;
                targetGrid.insert(gridPoint, gridPoint.getX() - gridPoint.getRadius(), gridPoint.getY() - gridPoint.getRadius(), size, size);
            } else if (fieldEffect instanceof Turret turret) {
                double size = turret.getRadius() * 2;
                targetGrid.insert(turret, turret.getX() - turret.getRadius(), turret.getY() - turret.getRadius(), size, size);
            }
        }
        for (Vehicle vehicle : entities.getVehicles()) {
            if (!vehicle.isDestroyed()) {
                targetGrid.insertPolygon(vehicle, vehicle.vertices());
            }
        }
    }

    /**
     * Applies collision resolution for a player against obstacles with sliding mechanics.
     * Uses predictive collision detection to prevent high-speed tunneling.
     *
     * @param player    The player to check collisions for
     * @param obstacles the obstacles in the field
     * @param delta     the time delta for this frame
     */
    public static void resolvePlayerObstacleCollisions(Player player, List<Obstacle> obstacles, long delta) {
        if (player == null || obstacles == null || obstacles.isEmpty()) {
            return;
        }

        Vector2D startPos = player.position();
        Vector2D vel = player.getVelocity();

        // Calculate intended movement for this frame
        double moveX = vel.x() * delta;
        double moveY = vel.y() * delta;
        Vector2D intendedPos = new Vector2D(startPos.x() + moveX, startPos.y() + moveY);

        // Use predictive collision detection with sliding
        Vector2D finalPos = resolveMovementWithSliding(startPos, intendedPos, PLAYER_RADIUS, obstacles, player);

        // Update player position to the final position
        player.setX(finalPos.x());
        player.setY(finalPos.y());
    }

    /**
     * Resolves movement with proper sliding mechanics while preventing tunneling.
     */
    private static Vector2D resolveMovementWithSliding(Vector2D startPos, Vector2D intendedPos, double radius, List<Obstacle> obstacles, Player player) {
        Vector2D currentPos = startPos;
        Vector2D remainingMovement = new Vector2D(intendedPos.x() - startPos.x(), intendedPos.y() - startPos.y());

        // Allow up to 3 collision iterations for complex geometry
        for (int iteration = 0; iteration < 3; iteration++) {
            Vector2D nextIntendedPos = new Vector2D(currentPos.x() + remainingMovement.x(), currentPos.y() + remainingMovement.y());

            // Check for collisions along the remaining movement
            CollisionResult result = findSafeMovementPosition(currentPos, nextIntendedPos, radius, obstacles);

            if (result.collidingObstacle == null) {
                // No collision, we can move to the intended position
                return result.position;
            }

            // We hit an obstacle, move to the collision point
            currentPos = result.position;

            // Calculate how much movement we have left
            Vector2D actualMovement = new Vector2D(currentPos.x() - startPos.x(), currentPos.y() - startPos.y());
            Vector2D totalIntendedMovement = new Vector2D(intendedPos.x() - startPos.x(), intendedPos.y() - startPos.y());

            // Calculate remaining movement magnitude
            double totalDistance = Math.sqrt(totalIntendedMovement.x() * totalIntendedMovement.x() + totalIntendedMovement.y() * totalIntendedMovement.y());
            double actualDistance = Math.sqrt(actualMovement.x() * actualMovement.x() + actualMovement.y() * actualMovement.y());
            double remainingDistance = totalDistance - actualDistance;

            if (remainingDistance <= 0.1) {
                // Very little movement left, stop here
                break;
            }

            // Calculate the collision normal and slide along the surface
            Vector2D normal = calculateCollisionNormal(currentPos, result.collidingObstacle);

            // Project the remaining movement onto the surface (perpendicular to normal)
            double dotProduct = remainingMovement.x() * normal.x() + remainingMovement.y() * normal.y();
            Vector2D projectedMovement = new Vector2D(
                    remainingMovement.x() - dotProduct * normal.x(),
                    remainingMovement.y() - dotProduct * normal.y()
            );

            // Update velocity for sliding (only if we're moving into the obstacle)
            Vector2D vel = player.getVelocity();
            double velDotNormal = vel.x() * normal.x() + vel.y() * normal.y();
            if (velDotNormal < 0) {
                Vector2D newVel = new Vector2D(
                        vel.x() - velDotNormal * normal.x(),
                        vel.y() - velDotNormal * normal.y()
                );
                player.setVelocity(newVel);
            }

            // Continue with the projected movement
            remainingMovement = projectedMovement;

            // If the projected movement is very small, stop
            double projectedLength = Math.sqrt(projectedMovement.x() * projectedMovement.x() + projectedMovement.y() * projectedMovement.y());
            if (projectedLength < 0.1) {
                break;
            }
        }

        return currentPos;
    }

    /**
     * Finds a safe position for the player to move to, preventing tunneling through obstacles.
     * Returns both the safe position and the obstacle that caused the collision (if any).
     */
    private static CollisionResult findSafeMovementPosition(Vector2D startPos, Vector2D intendedPos, double radius, List<Obstacle> obstacles) {
        // If no movement intended, return start position
        if (startPos.equals(intendedPos)) {
            return new CollisionResult(startPos, null);
        }

        // Check if the intended position would cause collisions
        for (Obstacle obstacle : obstacles) {
            // Quick broad-phase check
            if (!CollisionUtils.checkLineCircleCollision(startPos, intendedPos, obstacle.getCenter(), obstacle.getBoundingRadius() + radius)) {
                continue;
            }

            // Check if the movement path intersects the obstacle
            if (CollisionUtils.checkLinePolygonCollision(startPos, intendedPos, obstacle.getVertices()) ||
                CollisionUtils.checkCirclePolygonCollision(intendedPos, radius, obstacle.getVertices())) {

                // Find the safe position along the movement path
                Vector2D safePos = findSafePositionAlongPath(startPos, intendedPos, radius, obstacle);
                return new CollisionResult(safePos, obstacle);
            }
        }

        return new CollisionResult(intendedPos, null); // No collisions, intended position is safe
    }

    /**
     * Helper class to return both position and colliding obstacle
     */
    private static class CollisionResult {
        final Vector2D position;
        final Obstacle collidingObstacle;

        CollisionResult(Vector2D position, Obstacle collidingObstacle) {
            this.position = position;
            this.collidingObstacle = collidingObstacle;
        }
    }

    /**
     * Finds a safe position along the movement path when a collision is detected.
     */
    private static Vector2D findSafePositionAlongPath(Vector2D startPos, Vector2D intendedPos, double radius, Obstacle obstacle) {
        // Binary search for the furthest safe position along the path
        Vector2D safePos = startPos;
        double low = 0.0;
        double high = 1.0;

        for (int i = 0; i < 10; i++) { // Limit iterations for performance
            double mid = (low + high) / 2.0;
            Vector2D testPos = new Vector2D(
                    startPos.x() + (intendedPos.x() - startPos.x()) * mid,
                    startPos.y() + (intendedPos.y() - startPos.y()) * mid
            );

            if (!CollisionUtils.checkCirclePolygonCollision(testPos, radius, obstacle.getVertices())) {
                safePos = testPos;
                low = mid;
            } else {
                high = mid;
            }
        }

        return safePos;
    }


    /**
     * Calculates the collision normal for a position relative to an obstacle.
     */
    private static Vector2D calculateCollisionNormal(Vector2D pos, Obstacle obstacle) {
        List<Vector2D> verts = obstacle.getVertices();
        double bestDistSq = Double.POSITIVE_INFINITY;
        double closestX = 0, closestY = 0;

        // Find closest point on obstacle perimeter
        for (int i = 0; i < verts.size(); i++) {
            Vector2D a = verts.get(i);
            Vector2D b = verts.get((i + 1) % verts.size());

            // Project pos onto segment a-b
            double ax = a.x(), ay = a.y();
            double bx = b.x(), by = b.y();
            double lx = bx - ax, ly = by - ay;
            double l2 = lx * lx + ly * ly;
            double t = 0;
            if (l2 != 0) {
                t = ((pos.x() - ax) * lx + (pos.y() - ay) * ly) / l2;
                t = Math.max(0, Math.min(1, t));
            }
            double projX = ax + t * lx;
            double projY = ay + t * ly;
            double dx = pos.x() - projX;
            double dy = pos.y() - projY;
            double distSq = dx * dx + dy * dy;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                closestX = projX;
                closestY = projY;
            }
        }

        // Calculate normal from closest point to player position
        double nx = pos.x() - closestX;
        double ny = pos.y() - closestY;
        double nLen = Math.sqrt(nx * nx + ny * ny);
        if (nLen < 1e-6) {
            // Fallback to vector from obstacle center
            nx = pos.x() - obstacle.getCenter().x();
            ny = pos.y() - obstacle.getCenter().y();
            nLen = Math.sqrt(nx * nx + ny * ny);
            if (nLen < 1e-6) {
                return new Vector2D(0, 1); // Default normal
            }
        }

        return new Vector2D(nx / nLen, ny / nLen);
    }

    /**
     * Constrains a player's position within game boundaries and ensures they're not stuck in obstacles
     *
     * @param player The player to constrain
     */
    public void constrainPlayerToBounds(Player player) {
        double originalX = player.getX();
        double originalY = player.getY();

        // Apply basic boundary constraints
        player.setX(CollisionUtils.constrain(player.getX(), PLAYER_RADIUS, GAME_WIDTH - PLAYER_RADIUS));
        player.setY(CollisionUtils.constrain(player.getY(), PLAYER_RADIUS, GAME_HEIGHT - PLAYER_RADIUS));

        // If position changed due to boundary constraints, check for obstacle collisions
        if (player.getX() != originalX || player.getY() != originalY) {
            if (isColliding(player, entities.getObstacles())) {
                // Player is stuck in an obstacle after boundary constraint, find a safe position
                Vector2D safePosition = findSafePositionNearBoundary(player, originalX, originalY);
                player.setX(safePosition.x());
                player.setY(safePosition.y());
            }
        }

        // Final safety check: ensure player is never stuck inside obstacles
        validatePlayerNotStuckInObstacles(player);
    }

    /**
     * Validates that a player is not stuck inside obstacles and moves them to safety if needed.
     */
    private void validatePlayerNotStuckInObstacles(Player player) {
        if (isColliding(player, entities.getObstacles())) {
            // Player is stuck, find the nearest safe position
            Vector2D safePosition = findNearestSafePosition(player.position(), PLAYER_RADIUS, entities.getObstacles());
            player.setX(safePosition.x());
            player.setY(safePosition.y());
            // Stop the player's movement to prevent further issues
            player.setVelocity(Vector2D.ZERO);
        }
    }

    /**
     * Finds a safe position near the boundary when a player gets constrained.
     */
    private Vector2D findSafePositionNearBoundary(Player player, double originalX, double originalY) {
        Vector2D currentPos = player.position();

        // Try moving the player inward from the boundary in small steps
        for (int distance = 5; distance <= 50; distance += 5) {
            // Try moving toward the center of the map
            double centerX = GAME_WIDTH / 2.0;
            double centerY = GAME_HEIGHT / 2.0;
            double dirX = centerX - currentPos.x();
            double dirY = centerY - currentPos.y();
            double length = Math.sqrt(dirX * dirX + dirY * dirY);

            if (length > 0) {
                dirX /= length;
                dirY /= length;

                Vector2D testPos = new Vector2D(
                        currentPos.x() + dirX * distance,
                        currentPos.y() + dirY * distance
                );

                // Ensure test position is within bounds
                testPos = new Vector2D(
                        CollisionUtils.constrain(testPos.x(), PLAYER_RADIUS, GAME_WIDTH - PLAYER_RADIUS),
                        CollisionUtils.constrain(testPos.y(), PLAYER_RADIUS, GAME_HEIGHT - PLAYER_RADIUS)
                );

                if (!CollisionUtils.checkCirclePolygonCollision(testPos, PLAYER_RADIUS,
                        entities.getObstacles().stream().flatMap(o -> o.getVertices().stream()).toList())) {
                    return testPos;
                }
            }
        }

        // Last resort: return to original position
        return new Vector2D(originalX, originalY);
    }

    /**
     * Finds the nearest safe position for a player that's stuck in obstacles.
     */
    private Vector2D findNearestSafePosition(Vector2D stuckPos, double radius, List<Obstacle> obstacles) {
        // Try positions in expanding circles around the stuck position
        for (int searchRadius = 5; searchRadius <= 100; searchRadius += 5) {
            for (int angle = 0; angle < 360; angle += 15) {
                double radians = Math.toRadians(angle);
                Vector2D testPos = new Vector2D(
                        stuckPos.x() + Math.cos(radians) * searchRadius,
                        stuckPos.y() + Math.sin(radians) * searchRadius
                );

                // Ensure position is within game bounds
                if (testPos.x() < radius || testPos.x() > GAME_WIDTH - radius ||
                    testPos.y() < radius || testPos.y() > GAME_HEIGHT - radius) {
                    continue;
                }

                // Check if this position is safe
                boolean isSafe = true;
                for (Obstacle obstacle : obstacles) {
                    if (CollisionUtils.checkCirclePolygonCollision(testPos, radius, obstacle.getVertices())) {
                        isSafe = false;
                        break;
                    }
                }

                if (isSafe) {
                    return testPos;
                }
            }
        }

        // Emergency fallback: center of the map
        return new Vector2D(GAME_WIDTH / 2.0, GAME_HEIGHT / 2.0);
    }

    /**
     * Checks if a player is colliding with any obstacles in a list
     *
     * @param player         The player to check
     * @param checkObstacles The list of obstacles to check against
     * @return true if collision detected
     */
    public boolean isColliding(Player player, List<Obstacle> checkObstacles) {
        for (Obstacle obstacle : checkObstacles) {
            if (isColliding(player, obstacle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a player is colliding with a specific obstacle
     *
     * @param player   The player to check
     * @param obstacle The obstacle to check against
     * @return true if collision detected
     */
    public boolean isColliding(Player player, Obstacle obstacle) {
        // --- Broad Phase Check ---
        // First, do a quick check using bounding circles to see if the objects are even close.
        double combinedRadius = PLAYER_RADIUS + obstacle.getBoundingRadius();
        double distanceSq = player.position().distanceSquared(obstacle.getCenter());

        // If the distance between centers is greater than their combined radii, they can't be colliding.
        if (distanceSq > combinedRadius * combinedRadius) {
            return false;
        }

        // --- Narrow Phase Check ---
        // The broad phase passed, so now we do the expensive, precise check.
        return CollisionUtils.checkCirclePolygonCollision(player.position(), PLAYER_RADIUS, obstacle.vertices());
    }

    /**
     * Checks if a player is colliding with a power-up
     *
     * @param player  The player to check
     * @param powerUp The power-up to check against
     * @return true if collision detected
     */
    public boolean isColliding(Player player, PowerUp powerUp) {
        return player.position().distanceSquared(powerUp.getPosition()) < (PLAYER_SIZE * PLAYER_SIZE); // Using squared distance for efficiency
    }

    /**
     * Checks if a vehicle is colliding with any obstacles
     *
     * @param vehicle   The vehicle to check
     * @param obstacles The list of obstacles to check against
     * @return true if collision detected
     */
    public boolean isColliding(Vehicle vehicle, List<? extends Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (vehicle.id() != obstacle.id() && CollisionUtils.areObstaclesColliding(vehicle, obstacle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates if a vehicle can be placed at a specific position without colliding with obstacles
     *
     * @param vehicle     The vehicle to check
     * @param position    The position to test
     * @param minDistance Minimum distance from obstacles
     * @return true if the position is valid
     */
    public boolean isValidVehiclePosition(Vehicle vehicle, Vector2D position, double minDistance) {
        Vector2D originalPosition = vehicle.position();
        vehicle.setPosition(position);

        boolean isValid = true;

        for (Obstacle obstacle : entities.getObstacles()) {
            if (CollisionUtils.checkObstacleOverlap(vehicle, obstacle, minDistance)) {
                isValid = false;
                break;
            }
        }
        if (isValid) {
            for (Vehicle otherVehicle : entities.getVehicles()) {
                if (CollisionUtils.checkObstacleOverlap(vehicle, otherVehicle, minDistance)) {
                    isValid = false;
                    break;
                }
            }
        }

        // Restore original position
        vehicle.setPosition(originalPosition);
        return isValid;
    }
}
