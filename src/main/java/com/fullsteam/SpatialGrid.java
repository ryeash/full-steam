package com.fullsteam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private final List<T>[][] grid;

    @SuppressWarnings("unchecked")
    public SpatialGrid(double worldWidth, double worldHeight, double cellWidth, double cellHeight) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.cols = (int) Math.ceil(worldWidth / cellWidth);
        this.rows = (int) Math.ceil(worldHeight / cellHeight);
        this.grid = (List<T>[][]) new List[cols][rows];
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                grid[i][j] = new ArrayList<>();
            }
        }
    }

    public void clear() {
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                grid[i][j].clear();
            }
        }
    }

    public void insert(T object, double x, double y, double width, double height) {
        int startCol = (int) (x / cellWidth);
        int endCol = (int) ((x + width) / cellWidth);
        int startRow = (int) (y / cellHeight);
        int endRow = (int) ((y + height) / cellHeight);

        for (int i = Math.max(0, startCol); i <= Math.min(cols - 1, endCol); i++) {
            for (int j = Math.max(0, startRow); j <= Math.min(rows - 1, endRow); j++) {
                grid[i][j].add(object);
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
                nearbyObjects.addAll(grid[i][j]);
            }
        }
        return nearbyObjects;
    }
}