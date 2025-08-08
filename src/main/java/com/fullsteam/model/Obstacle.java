package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class Obstacle implements HasId {

    private static final AtomicLong idCounter = new AtomicLong(0);

    private final long id;
    private final List<Vector2D> vertices;
    @JsonIgnore
    private final Vector2D center;
    @JsonIgnore
    private final double boundingRadius;
    @JsonIgnore
    private final boolean rendered;

    public Obstacle(List<Vector2D> vertices) {
        this(vertices, true);
    }

    public Obstacle(List<Vector2D> vertices, boolean rendered) {
        this.id = idCounter.incrementAndGet();
        this.vertices = vertices;

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

    public static Obstacle createRandomPolygonObstacle() {
        int vertexCount = 3 + ThreadLocalRandom.current().nextInt(5); // Polygons with 3 to 7 vertices
        double centerX = 100 + ThreadLocalRandom.current().nextInt(Config.GAME_WIDTH - 200);
        double centerY = 100 + ThreadLocalRandom.current().nextInt(Config.GAME_HEIGHT - 200);
        double avgRadius = 40 + ThreadLocalRandom.current().nextInt(60);

        List<Vector2D> points = new ArrayList<>();
        for (int i = 0; i < vertexCount; i++) {
            double angle = ((double) i / (double) vertexCount) * 2 * Math.PI;
            double radius = avgRadius * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.4); // Add some irregularity
            double x = centerX + radius * Math.cos(angle);
            double y = centerY + radius * Math.sin(angle);
            points.add(new Vector2D(x, y));
        }
        return new Obstacle(points);
    }

    public static Obstacle createSymmetricPolygonObstacle() {
        final double centerX = Config.GAME_WIDTH / 2.0;
        final double centerY = 100 + ThreadLocalRandom.current().nextInt(Config.GAME_HEIGHT - 200);
        final double halfWidth = 20 + ThreadLocalRandom.current().nextInt(80);
        final double halfHeight = 30 + ThreadLocalRandom.current().nextInt(100);

        final Vector2D topPoint = new Vector2D(centerX, centerY - halfHeight);
        final Vector2D bottomPoint = new Vector2D(centerX, centerY + halfHeight);

        int midPointsCount = 1 + ThreadLocalRandom.current().nextInt(3);
        List<Vector2D> rightSideMidPoints = new ArrayList<>();
        for (int i = 0; i < midPointsCount; i++) {
            double pointX = centerX + (ThreadLocalRandom.current().nextDouble() * halfWidth);
            double pointY = (centerY - halfHeight) + (ThreadLocalRandom.current().nextDouble() * (halfHeight * 2));
            rightSideMidPoints.add(new Vector2D(pointX, pointY));
        }

        List<Vector2D> rightSideVertices = new ArrayList<>();
        rightSideVertices.add(topPoint);
        rightSideVertices.addAll(rightSideMidPoints);
        rightSideVertices.add(bottomPoint);
        rightSideVertices.sort(Comparator.comparingDouble(Vector2D::y));

        List<Vector2D> leftSideVertices = rightSideMidPoints.stream()
                .map(v -> new Vector2D(Config.GAME_WIDTH - v.x(), v.y()))
                .sorted(Comparator.comparingDouble(Vector2D::y).reversed())
                .toList();

        List<Vector2D> allVertices = new ArrayList<>(rightSideVertices);
        allVertices.addAll(leftSideVertices);

        return new Obstacle(allVertices);
    }

    public Obstacle createMirrorClone() {
        List<Vector2D> mirroredVertices = this.vertices.stream()
                .map(vertex -> new Vector2D(Config.GAME_WIDTH - vertex.x(), vertex.y()))
                .toList();
        return new Obstacle(mirroredVertices);
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
}
