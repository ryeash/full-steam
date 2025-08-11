package com.fullsteam;

import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Vector2D;

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

    /**
     * Checks if a line segment intersects with an axis-aligned rectangle.
     *
     * @param lineStart The start point of the line segment
     * @param lineEnd   The end point of the line segment
     * @param rectX     The x coordinate of the rectangle's top-left corner
     * @param rectY     The y coordinate of the rectangle's top-left corner
     * @param rectW     The width of the rectangle
     * @param rectH     The height of the rectangle
     * @return true if the line intersects the rectangle, false otherwise
     */
    public static boolean checkLineRectangleCollision(Vector2D lineStart, Vector2D lineEnd,
                                                      double rectX, double rectY,
                                                      double rectW, double rectH) {
        // First check if either endpoint is inside the rectangle
        if (isPointInRectangle(lineStart, rectX, rectY, rectW, rectH) ||
                isPointInRectangle(lineEnd, rectX, rectY, rectW, rectH)) {
            return true;
        }

        // Create the four edges of the rectangle
        Vector2D topLeft = new Vector2D(rectX, rectY);
        Vector2D topRight = new Vector2D(rectX + rectW, rectY);
        Vector2D bottomLeft = new Vector2D(rectX, rectY + rectH);
        Vector2D bottomRight = new Vector2D(rectX + rectW, rectY + rectH);

        return checkLinePolygonCollision(lineStart, lineEnd, List.of(topLeft, topRight, bottomRight, bottomLeft));
    }

    private static boolean isPointInRectangle(Vector2D point, double rectX, double rectY,
                                              double rectW, double rectH) {
        return point.x() >= rectX && point.x() <= rectX + rectW &&
                point.y() >= rectY && point.y() <= rectY + rectH;
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
        return distanceToSegment(circleCenter, lineStart, lineEnd) < radius;
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
}