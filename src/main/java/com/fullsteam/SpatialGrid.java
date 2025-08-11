package com.fullsteam;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A spatial partitioning grid to optimize collision detection and proximity queries.
 * It divides the game world into a grid of cells and stores objects in the cells they overlap.
 * This allows for checking against a small subset of objects instead of all objects in the world.
 *
 * @param <T> The type of object to be stored in the grid.
 */
public class SpatialGrid<T> {
    private final int cols;
    private final int rows;
    private final double cellWidth;
    private final double cellHeight;
    private final Map<Long, List<T>> sparseMatrix;

    public SpatialGrid(double worldWidth, double worldHeight, double cellWidth, double cellHeight) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.cols = (int) Math.ceil(worldWidth / cellWidth);
        this.rows = (int) Math.ceil(worldHeight / cellHeight);
        this.sparseMatrix = new ConcurrentHashMap<>(20, 1, 1);
    }

    public void clear() {
        sparseMatrix.clear();
    }

    public void insert(T object, double x, double y, double width, double height) {
        int startCol = (int) (x / cellWidth);
        int endCol = (int) ((x + width) / cellWidth);
        int startRow = (int) (y / cellHeight);
        int endRow = (int) ((y + height) / cellHeight);

        for (int i = Math.max(0, startCol); i <= Math.min(cols - 1, endCol); i++) {
            for (int j = Math.max(0, startRow); j <= Math.min(rows - 1, endRow); j++) {
                sparseMatrix.computeIfAbsent(toKey(i, j), v -> new LinkedList<>()).add(object);
            }
        }
    }

    public Set<T> getNearby(double x, double y, double width, double height) {
        Set<T> nearbyObjects = new HashSet<>();
        int startCol = (int) (x / cellWidth);
        int endCol = (int) ((x + width) / cellWidth);
        int startRow = (int) (y / cellHeight);
        int endRow = (int) ((y + height) / cellHeight);

        for (int i = Math.max(0, startCol); i <= Math.min(cols - 1, endCol); i++) {
            for (int j = Math.max(0, startRow); j <= Math.min(rows - 1, endRow); j++) {
                List<T> t = sparseMatrix.get(toKey(i, j));
                if (t != null) {
                    nearbyObjects.addAll(t);
                }
            }
        }
        return nearbyObjects;
    }

    private long toKey(int col, int row) {
        return ((long) col << 32) | (row & 0xFFFFFFFFL);
    }
}