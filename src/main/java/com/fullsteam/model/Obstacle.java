package com.fullsteam.model;

import com.fullsteam.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a polygonal obstacle defined by a list of vertices.
 */
public record Obstacle(List<Vector2D> vertices) {

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

    /**
     * Creates a single, symmetrical obstacle centered on the vertical axis of the game world.
     * This is useful for creating fair and balanced maps.
     *
     * @return A new Obstacle instance that is horizontally symmetrical.
     */
    public static Obstacle createSymmetricPolygonObstacle() {
        // Determine the center line and random dimensions for one half
        final double centerX = Config.GAME_WIDTH / 2.0;
        final double centerY = 100 + ThreadLocalRandom.current().nextInt(Config.GAME_HEIGHT - 200);
        final double halfWidth = 20 + ThreadLocalRandom.current().nextInt(80);
        final double halfHeight = 30 + ThreadLocalRandom.current().nextInt(100);

        // Define the top and bottom points that lie on the center line, forming the seam
        final Vector2D topPoint = new Vector2D(centerX, centerY - halfHeight);
        final Vector2D bottomPoint = new Vector2D(centerX, centerY + halfHeight);

        // Generate a few random mid-points for the right side
        int midPointsCount = 1 + ThreadLocalRandom.current().nextInt(3); // 1 to 3 mid-points
        List<Vector2D> rightSideMidPoints = new ArrayList<>();
        for (int i = 0; i < midPointsCount; i++) {
            // X is offset from the center, Y is within the vertical bounds
            double pointX = centerX + (ThreadLocalRandom.current().nextDouble() * halfWidth);
            double pointY = (centerY - halfHeight) + (ThreadLocalRandom.current().nextDouble() * (halfHeight * 2));
            rightSideMidPoints.add(new Vector2D(pointX, pointY));
        }

        // Combine the right-side points and sort them vertically to ensure a valid polygon edge
        List<Vector2D> rightSideVertices = new ArrayList<>();
        rightSideVertices.add(topPoint);
        rightSideVertices.addAll(rightSideMidPoints);
        rightSideVertices.add(bottomPoint);
        rightSideVertices.sort(Comparator.comparingDouble(Vector2D::y));

        // Create the mirrored left-side points (excluding top and bottom which are already on the line)
        // Sort them in reverse to create a continuous path for the polygon
        List<Vector2D> leftSideVertices = rightSideMidPoints.stream()
                .map(v -> new Vector2D(Config.GAME_WIDTH - v.x(), v.y()))
                .sorted(Comparator.comparingDouble(Vector2D::y).reversed())
                .toList();

        // Combine both lists to form the final, closed polygon
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

    public static Obstacle createRectangle(double x, double y, double width, double height) {
        List<Vector2D> vertices = new ArrayList<>();
        vertices.add(new Vector2D(x, y));
        vertices.add(new Vector2D(x + width, y));
        vertices.add(new Vector2D(x + width, y + height));
        vertices.add(new Vector2D(x, y + height));
        return new Obstacle(vertices);
    }
}