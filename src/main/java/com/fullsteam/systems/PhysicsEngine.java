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

    // smallest double that we consider non-zero
    private static final double EPS = 1e-3;

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
     * Applies collision resolution for a player against obstacles with sliding mechanics
     *
     * @param player    The player to check collisions for
     * @param obstacles the obstacles in the field
     */
    // java
    public static void resolvePlayerObstacleCollisions(Player player, List<Obstacle> obstacles) {
        if (player == null || obstacles == null || obstacles.isEmpty()) {
            return;
        }

        Vector2D pos = player.position();
        Vector2D vel = player.getVelocity();
        double radius = PLAYER_RADIUS;

        for (Obstacle obstacle : obstacles) {
            if (!CollisionUtils.checkLineCircleCollision(pos, pos, obstacle.getCenter(), obstacle.getBoundingRadius() + radius)) {
                continue;
            }

            List<Vector2D> verts = obstacle.getVertices();
            // Find closest point on polygon edges to the circle center
            double bestDistSq = Double.POSITIVE_INFINITY;
            double closestX = 0, closestY = 0;
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

            double dist = Math.sqrt(bestDistSq);
            double penetration = radius - dist;
            if (penetration > 0) {
                // Compute collision normal (from obstacle toward player)
                double nx = pos.x() - closestX;
                double ny = pos.y() - closestY;
                double nLen = Math.sqrt(nx * nx + ny * ny);
                if (nLen < 1e-6) {
                    // Degenerate case: fallback to vector from obstacle center
                    nx = pos.x() - obstacle.getCenter().x();
                    ny = pos.y() - obstacle.getCenter().y();
                    nLen = Math.sqrt(nx * nx + ny * ny);
                    if (nLen < 1e-6) {
                        nx = 0;
                        ny = 1;
                        nLen = 1;
                    }
                }
                nx /= nLen;
                ny /= nLen;

                // Push the player out along the normal by penetration + small epsilon
                double moveX = (penetration + EPS) * nx;
                double moveY = (penetration + EPS) * ny;
                player.setX(pos.x() + moveX);
                player.setY(pos.y() + moveY);
                // Update pos for subsequent obstacles
                pos = player.position();

                // Remove only the velocity component along the normal (keep tangential component => sliding)
                double vnx = vel.x() * nx + vel.y() * ny; // dot(vel, normal)
                if (vnx < 0) {
                    // Only remove incoming component (when pointing into the obstacle)
                    double newVelX = vel.x() - vnx * nx;
                    double newVelY = vel.y() - vnx * ny;
                    vel = new Vector2D(newVelX, newVelY);
                    player.setVelocity(vel);
                }
            }
        }
    }

    /**
     * Constrains a player's position within game boundaries
     *
     * @param player The player to constrain
     */
    public void constrainPlayerToBounds(Player player) {
        player.setX(CollisionUtils.constrain(player.getX(), PLAYER_RADIUS, GAME_WIDTH - PLAYER_RADIUS));
        player.setY(CollisionUtils.constrain(player.getY(), PLAYER_RADIUS, GAME_HEIGHT - PLAYER_RADIUS));
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
