package com.fullsteam.systems;

import com.fullsteam.CollisionUtils;
import com.fullsteam.model.GameEntities;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.PowerUp;

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
        entities.populateSpatialGrids();
    }

    /**
     * Applies collision resolution for a player against obstacles with sliding mechanics
     * @param player The player to check collisions for
     * @param oldX The player's previous X position
     * @param oldY The player's previous Y position
     * @return true if the player's position was modified due to collision
     */
    public boolean resolvePlayerObstacleCollisions(Player player, double oldX, double oldY) {
        if (!isColliding(player, entities.getObstacles())) {
            return false;
        }

        // Player's new position is invalid. Attempt to slide along the obstacle.
        // This is done by testing movement on each axis independently.
        
        // First, try moving only on the Y axis.
        player.setX(oldX);
        if (isColliding(player, entities.getObstacles())) {
            // That didn't work, so the Y-move was the problem.
            // Revert Y and try moving only on the X axis.
            player.setY(oldY);
            player.setX(oldX + player.getVelocityX());
            if (isColliding(player, entities.getObstacles())) {
                // Still colliding, can't move on X either. Revert both.
                player.setX(oldX);
                return true;
            }
        }
        return true;
    }

    /**
     * Constrains a player's position within game boundaries
     * @param player The player to constrain
     */
    public void constrainPlayerToBounds(Player player) {
        player.setX(CollisionUtils.constrain(player.getX(), PLAYER_RADIUS, GAME_WIDTH - PLAYER_RADIUS));
        player.setY(CollisionUtils.constrain(player.getY(), PLAYER_RADIUS, GAME_HEIGHT - PLAYER_RADIUS));
    }

    /**
     * Checks if a player is colliding with any obstacles in a list
     * @param player The player to check
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
     * @param player The player to check
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
     * @param player The player to check
     * @param powerUp The power-up to check against
     * @return true if collision detected
     */
    public boolean isColliding(Player player, PowerUp powerUp) {
        return player.position().distanceSquared(powerUp.getPosition()) < (PLAYER_SIZE * PLAYER_SIZE); // Using squared distance for efficiency
    }

    /**
     * Checks if a vehicle is colliding with any obstacles
     * @param vehicle The vehicle to check
     * @param obstacles The list of obstacles to check against
     * @return true if collision detected
     */
    public boolean isColliding(Vehicle vehicle, List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (CollisionUtils.areObstaclesColliding(vehicle, obstacle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a position is out of bounds
     * @param position The position to check
     * @return true if the position is outside game boundaries
     */
    public boolean isOutOfBounds(Vector2D position) {
        return position.x() < 0 
               || position.x() > GAME_WIDTH 
               || position.y() < 0 
               || position.y() > GAME_HEIGHT;
    }

    /**
     * Checks if a point is within game bounds with a given radius
     * @param x The x coordinate
     * @param y The y coordinate
     * @param radius The radius to check
     * @return true if the point (including radius) is within bounds
     */
    public boolean isWithinBounds(double x, double y, double radius) {
        return x >= radius && x <= GAME_WIDTH - radius && 
               y >= radius && y <= GAME_HEIGHT - radius;
    }

    /**
     * Validates if a vehicle can be placed at a specific position without colliding with obstacles
     * @param vehicle The vehicle to check
     * @param position The position to test
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
        
        // Restore original position
        vehicle.setPosition(originalPosition);
        return isValid;
    }

    /**
     * Gets the spatial grid for advanced collision queries
     * @return The spatial grid containing all targetable entities
     */
    public com.fullsteam.SpatialGrid<com.fullsteam.model.Targetable> getSpatialGrid() {
        return entities.getTargetGrid();
    }
}
