package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Introspected
public class Obstacle implements HasId {

    private static final AtomicLong idCounter = new AtomicLong(0);

    private final long id;
    private final List<Vector2D> vertices;
    private final boolean rendered;
    @JsonIgnore
    private Vector2D center;
    @JsonIgnore
    private final double boundingRadius;

    public Obstacle(List<Vector2D> vertices) {
        this(vertices, true);
    }

    public Obstacle(List<Vector2D> vertices, boolean rendered) {
        this.id = idCounter.incrementAndGet();
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("obstacles must be polygons with at least 3 vertices");
        }
        this.vertices = new ArrayList<>(vertices);

        // --- Calculate Bounding Information ---
        // 1. Find the geometric center (centroid) of the polygon.
        double totalX = 0, totalY = 0;
        for (Vector2D v : vertices) {
            totalX += v.x();
            totalY += v.y();
        }
        this.center = new Vector2D(totalX / vertices.size(), totalY / vertices.size());

        // 2. Find the bounding radius (distance from center to the furthest vertex).
        this.boundingRadius = Math.sqrt(vertices.stream()
                .mapToDouble(v -> v.distanceSquared(this.center))
                .max()
                .orElse(0.0));

        this.rendered = rendered;
    }

    @Override
    public long id() {
        return id;
    }

    public List<Vector2D> getVertices() {
        return vertices;
    }

    public List<Vector2D> vertices() {
        return vertices;
    }

    public Vector2D getCenter() {
        return center;
    }

    public double getBoundingRadius() {
        return boundingRadius;
    }

    /**
     * Indicates whether an obstacle should be rendered (sent to the client)
     * or just used for collision detection.
     *
     * @return true if this obstacle is rendered in the UI, false if it's only used for collision detection
     */
    public boolean isRendered() {
        return rendered;
    }

    /**
     * Rotates all vertices around the center point by the specified angle in radians.
     *
     * @param angleRadians The angle to rotate in radians
     */
    public void rotate(double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);

        for (int i = 0; i < vertices.size(); i++) {
            Vector2D v = vertices.get(i);

            // Translate point to origin
            double dx = v.x() - center.x();
            double dy = v.y() - center.y();

            // Rotate point
            double newX = dx * cos - dy * sin + center.x();
            double newY = dx * sin + dy * cos + center.y();

            // Update vertex
            vertices.set(i, new Vector2D(newX, newY));
        }
    }

    public void setPosition(Vector2D position) {
        double offsetX = position.x() - center.x();
        double offsetY = position.y() - center.y();

        for (int i = 0; i < vertices.size(); i++) {
            Vector2D v = vertices.get(i);
            vertices.set(i, new Vector2D(v.x() + offsetX, v.y() + offsetY));
        }

        // Update the center to the new position
        this.center = position;
    }

    public static Obstacle createRandomPolygonObstacle() {
        int vertexCount = 3 + ThreadLocalRandom.current().nextInt(6); // Polygons with 3 to 7 vertices
        double avgRadius = 45 + ThreadLocalRandom.current().nextInt(100);
        
        // Calculate safe bounds: ensure center is far enough from edges so that
        // even the furthest vertex (with irregularity) won't be too close to map edges
        double maxPossibleRadius = avgRadius * 1.2; // Account for irregularity (0.8 + 0.4 = 1.2 max)
        double edgeBuffer = 50; // Minimum distance from map edge
        double safeMargin = maxPossibleRadius + edgeBuffer;
        
        // Ensure we have enough space to place obstacles
        if (safeMargin * 2 >= Config.GAME_WIDTH || safeMargin * 2 >= Config.GAME_HEIGHT) {
            // Fallback for very large obstacles or small maps
            safeMargin = Math.min(Config.GAME_WIDTH, Config.GAME_HEIGHT) * 0.2;
        }
        
        double centerX = safeMargin + ThreadLocalRandom.current().nextDouble(Config.GAME_WIDTH - 2 * safeMargin);
        double centerY = safeMargin + ThreadLocalRandom.current().nextDouble(Config.GAME_HEIGHT - 2 * safeMargin);

        List<Vector2D> points = new ArrayList<>();
        for (int i = 0; i < vertexCount; i++) {
            double angle = ((double) i / (double) vertexCount) * 2 * Math.PI;
            double radius = avgRadius * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4); // Add some irregularity
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            
            // Double-check that vertices are within safe bounds (extra safety)
            x = Math.max(edgeBuffer, Math.min(Config.GAME_WIDTH - edgeBuffer, x));
            y = Math.max(edgeBuffer, Math.min(Config.GAME_HEIGHT - edgeBuffer, y));
            
            points.add(new Vector2D(x, y));
        }
        return new Obstacle(points);
    }

    public Obstacle create180Clone() {
        List<Vector2D> rotatedVertices = this.vertices.stream()
                .map(vertex -> new Vector2D(Config.GAME_WIDTH - vertex.x(), Config.GAME_HEIGHT - vertex.y()))
                .toList();
        return new Obstacle(rotatedVertices);
    }

    public static Obstacle createRectangle(double x, double y, double width, double height) {
        List<Vector2D> vertices = new ArrayList<>();
        vertices.add(new Vector2D(x, y));
        vertices.add(new Vector2D(x + width, y));
        vertices.add(new Vector2D(x + width, y + height));
        vertices.add(new Vector2D(x, y + height));
        return new Obstacle(vertices);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Obstacle obstacle = (Obstacle) o;
        return id == obstacle.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
