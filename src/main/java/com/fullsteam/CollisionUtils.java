package com.fullsteam;

import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Vector2D;

import java.util.ArrayList;
import java.util.List;

public class CollisionUtils {

    /**
     * Checks if a point is inside a convex polygon using the ray-casting algorithm.
     *
     * @param point   The point to check.
     * @param polygon The list of vertices defining the polygon.
     * @return true if the point is inside, false otherwise.
     */
    public static boolean isPointInsidePolygon(Vector2D point, List<Vector2D> polygon) {
        boolean isInside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vector2D p1 = polygon.get(i);
            Vector2D p2 = polygon.get(j);

            if (((p1.y() > point.y()) != (p2.y() > point.y())) &&
                    (point.x() < (p2.x() - p1.x()) * (point.y() - p1.y()) / (p2.y() - p1.y()) + p1.x())) {
                isInside = !isInside;
            }
        }
        return isInside;
    }

    /**
     * Checks for collision between a circle and a convex polygon.
     *
     * @param circleCenter The center of the circle.
     * @param radius       The radius of the circle.
     * @param polygon      The list of vertices defining the polygon.
     * @return true if they collide, false otherwise.
     */
    public static boolean checkCirclePolygonCollision(Vector2D circleCenter, double radius, List<Vector2D> polygon) {
        // 1. Check if the circle's center is inside the polygon.
        if (isPointInsidePolygon(circleCenter, polygon)) {
            return true;
        }

        // 2. Check if the circle is close enough to any of the polygon's edges.
        for (int i = 0; i < polygon.size(); i++) {
            Vector2D p1 = polygon.get(i);
            Vector2D p2 = polygon.get((i + 1) % polygon.size()); // Next vertex, wraps around
            if (distanceToSegment(circleCenter, p1, p2) < radius) {
                return true;
            }
        }

        return false;
    }

    /**
     * Calculates the shortest distance from a point to a line segment.
     */
    private static double distanceToSegment(Vector2D p, Vector2D v, Vector2D w) {
        double l2 = (v.x() - w.x()) * (v.x() - w.x()) + (v.y() - w.y()) * (v.y() - w.y());
        if (l2 == 0.0) {
            return Math.sqrt((p.x() - v.x()) * (p.x() - v.x()) + (p.y() - v.y()) * (p.y() - v.y()));
        }

        double t = Math.max(0, Math.min(1, ((p.x() - v.x()) * (w.x() - v.x()) + (p.y() - v.y()) * (w.y() - v.y())) / l2));
        double projX = v.x() + t * (w.x() - v.x());
        double projY = v.y() + t * (w.y() - v.y());

        return Math.sqrt((p.x() - projX) * (p.x() - projX) + (p.y() - projY) * (p.y() - projY));
    }

    private static double distanceToSegment2(Vector2D point, Vector2D segmentStart, Vector2D segmentEnd) {
        // Calculate the length of the segment squared
        double lengthSquared = segmentStart.distanceSquared(segmentEnd);

        // If segment is actually a point, just return distance to that point
        if (lengthSquared == 0) {
            return point.distance(segmentStart);
        }

        // Consider the line extending the segment, parameterized as segmentStart + t (segmentEnd - segmentStart)
        // Project point onto the line by finding parameter t
        double t = Math.max(0, Math.min(1,
                ((point.x() - segmentStart.x()) * (segmentEnd.x() - segmentStart.x()) +
                        (point.y() - segmentStart.y()) * (segmentEnd.y() - segmentStart.y())) / lengthSquared));

        // Find the projection point
        double projX = segmentStart.x() + t * (segmentEnd.x() - segmentStart.x());
        double projY = segmentStart.y() + t * (segmentEnd.y() - segmentStart.y());

        // Return distance to projection point
        return point.distance(new Vector2D(projX, projY));
    }

    /**
     * Checks if a line segment intersects with any edge of a polygon.
     *
     * @param p1       Start point of the line segment.
     * @param p2       End point of the line segment.
     * @param vertices The list of vertices defining the polygon.
     * @return true if an intersection occurs, false otherwise.
     */
    public static boolean checkLinePolygonCollision(Vector2D p1, Vector2D p2, List<Vector2D> vertices) {
        for (int i = 0; i < vertices.size(); i++) {
            Vector2D edgeP1 = vertices.get(i);
            Vector2D edgeP2 = vertices.get((i + 1) % vertices.size()); // Wrap around for the last edge
            if (checkLineLineIntersection(p1, p2, edgeP1, edgeP2)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkLinePolygonCollision(Vector2D p1, Vector2D p2, Obstacle obstacle) {
        return checkLineCircleCollision(p1, p2, obstacle.getCenter(), obstacle.getBoundingRadius())
                && checkLinePolygonCollision(p1, p2, obstacle.getVertices());
    }

    /**
     * Finds the intersection point between a line segment and an obstacle.
     * Returns null if no intersection is found.
     *
     * @param start    Starting point of the line segment
     * @param end      End point of the line segment
     * @param obstacle The obstacle to check collision with
     * @return The closest intersection point or null if no intersection exists
     */
    public static Vector2D findLineObstacleCollision(Vector2D start, Vector2D end, Obstacle obstacle) {
        if (obstacle == null
                || obstacle.getVertices() == null
                || obstacle.getVertices().size() < 3
                || !checkLineCircleCollision(start, end, obstacle.getCenter(), obstacle.getBoundingRadius())) {
            return null;
        }

        Vector2D closestIntersection = null;
        double minDistance = Double.MAX_VALUE;

        // Check intersection with each edge of the obstacle
        List<Vector2D> vertices = obstacle.getVertices();
        for (int i = 0; i < vertices.size(); i++) {
            Vector2D v1 = vertices.get(i);
            Vector2D v2 = vertices.get((i + 1) % vertices.size());

            Vector2D intersection = findLineSegmentIntersection(start, end, v1, v2);
            if (intersection != null) {
                double distance = start.distance(intersection);
                if (distance < minDistance) {
                    minDistance = distance;
                    closestIntersection = intersection;
                }
            }
        }

        return closestIntersection;
    }

    /**
     * Helper method to find intersection point between two line segments.
     * Returns null if no intersection exists.
     */
    private static Vector2D findLineSegmentIntersection(Vector2D p1, Vector2D p2, Vector2D p3, Vector2D p4) {
        // Calculate denominator for intersection check
        double denominator = (p2.x() - p1.x()) * (p4.y() - p3.y()) - (p2.y() - p1.y()) * (p4.x() - p3.x());
        if (denominator == 0) {
            return null; // Lines are parallel
        }

        double ua = ((p4.x() - p3.x()) * (p1.y() - p3.y()) - (p4.y() - p3.y()) * (p1.x() - p3.x())) / denominator;
        double ub = ((p2.x() - p1.x()) * (p1.y() - p3.y()) - (p2.y() - p1.y()) * (p1.x() - p3.x())) / denominator;

        // Check if intersection occurs within both line segments
        if (ua >= 0 && ua <= 1 && ub >= 0 && ub <= 1) {
            double x = p1.x() + ua * (p2.x() - p1.x());
            double y = p1.y() + ua * (p2.y() - p1.y());
            return new Vector2D(x, y);
        }

        return null;
    }

    /**
     * Checks for collision between a line segment and a circle.
     * This is useful for detecting collisions of fast-moving projectiles (represented as a line segment
     * of their travel in one frame) with circular hitboxes.
     *
     * @param lineStart    The starting point of the line segment.
     * @param lineEnd      The ending point of the line segment.
     * @param circleCenter The center of the circle.
     * @param radius       The radius of the circle.
     * @return true if the line segment intersects the circle, false otherwise.
     */
    public static boolean checkLineCircleCollision(Vector2D lineStart, Vector2D lineEnd, Vector2D circleCenter, double radius) {
        return distanceToSegment2(circleCenter, lineStart, lineEnd) < radius;
    }

    /**
     * Helper method to check for intersection between two line segments (p1-p2 and p3-p4).
     */
    private static boolean checkLineLineIntersection(Vector2D p1, Vector2D p2, Vector2D p3, Vector2D p4) {
        double x1 = p1.x(), y1 = p1.y();
        double x2 = p2.x(), y2 = p2.y();
        double x3 = p3.x(), y3 = p3.y();
        double x4 = p4.x(), y4 = p4.y();

        // Using the formula from https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection
        double denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (denominator == 0) {
            return false; // Lines are parallel
        }

        double t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denominator;
        double u = -((x1 - x2) * (y1 - y3) - (y1 - y2) * (x1 - x3)) / denominator;

        // If 0 <= t <= 1 and 0 <= u <= 1, the segments intersect.
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    public static boolean checkObstacleOverlap(Obstacle o1, Obstacle o2, double spacingBuffer) {
        double combinedRadius = o1.getBoundingRadius() + o2.getBoundingRadius() + spacingBuffer;
        return o1.getCenter().distanceSquared(o2.getCenter()) < combinedRadius * combinedRadius;
    }

    /**
     * Checks if two obstacles are colliding using the Separating Axis Theorem (SAT).
     *
     * @param a First obstacle
     * @param b Second obstacle
     * @return true if the obstacles are colliding, false otherwise
     */
    public static boolean areObstaclesColliding(Obstacle a, Obstacle b) {
        // Quick check using bounding circles first for performance
        double radiusSum = a.getBoundingRadius() + b.getBoundingRadius();
        if (a.getCenter().distance(b.getCenter()) > radiusSum) {
            return false;
        }

        List<Vector2D> verticesA = a.getVertices();
        List<Vector2D> verticesB = b.getVertices();

        // Get all edges from both polygons
        List<Vector2D> edges = new ArrayList<>();
        // Add edges from polygon A
        for (int i = 0; i < verticesA.size(); i++) {
            Vector2D v1 = verticesA.get(i);
            Vector2D v2 = verticesA.get((i + 1) % verticesA.size());
            edges.add(new Vector2D(v2.x() - v1.x(), v2.y() - v1.y()));
        }
        // Add edges from polygon B
        for (int i = 0; i < verticesB.size(); i++) {
            Vector2D v1 = verticesB.get(i);
            Vector2D v2 = verticesB.get((i + 1) % verticesB.size());
            edges.add(new Vector2D(v2.x() - v1.x(), v2.y() - v1.y()));
        }

        // Test each edge as a potential separating axis
        for (Vector2D edge : edges) {
            // Get the axis perpendicular to the edge
            Vector2D axis = new Vector2D(-edge.y(), edge.x());

            // Project both polygons onto the axis
            double[] projectionA = projectPolygon(verticesA, axis);
            double[] projectionB = projectPolygon(verticesB, axis);

            // Check if projections overlap
            if (!doProjectionsOverlap(projectionA, projectionB)) {
                // Found a separating axis, polygons are not colliding
                return false;
            }
        }

        // No separating axis found, polygons must be colliding
        return true;
    }

    /**
     * Projects a polygon onto an axis and returns the min/max values.
     */
    private static double[] projectPolygon(List<Vector2D> vertices, Vector2D axis) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (Vector2D vertex : vertices) {
            // Calculate dot product of vertex and axis
            double projection = vertex.x() * axis.x() + vertex.y() * axis.y();
            min = Math.min(min, projection);
            max = Math.max(max, projection);
        }

        return new double[]{min, max};
    }

    /**
     * Checks if two projections overlap.
     */
    private static boolean doProjectionsOverlap(double[] projectionA, double[] projectionB) {
        return !(projectionA[1] < projectionB[0] || projectionB[1] < projectionA[0]);
    }

    public static double constrain(double value, double min, double max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
}