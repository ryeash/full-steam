package com.fullsteam.model;

public class PlayerInput {
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private boolean shooting;
    private double mouseX;
    private double mouseY;
    private boolean reload;
    private boolean weaponCycle;

    public PlayerInput() {
    }

    public PlayerInput(boolean up, boolean down, boolean left, boolean right,
                       boolean shooting, double mouseX, double mouseY, boolean reload) {
        this.up = up;
        this.down = down;
        this.left = left;
        this.right = right;
        this.shooting = shooting;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.reload = reload;
    }

    // Getters and setters
    public boolean getUp() {
        return up;
    }

    public void setUp(boolean up) {
        this.up = up;
    }

    public boolean getDown() {
        return down;
    }

    public void setDown(boolean down) {
        this.down = down;
    }

    public boolean getLeft() {
        return left;
    }

    public void setLeft(boolean left) {
        this.left = left;
    }

    public boolean getRight() {
        return right;
    }

    public void setRight(boolean right) {
        this.right = right;
    }

    public boolean getShooting() {
        return shooting;
    }

    public void setShooting(boolean shooting) {
        this.shooting = shooting;
    }

    public double getMouseX() {
        return mouseX;
    }

    public void setMouseX(double mouseX) {
        this.mouseX = mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public void setMouseY(double mouseY) {
        this.mouseY = mouseY;
    }

    public boolean isReload() {
        return reload;
    }

    public void setReload(boolean reload) {
        this.reload = reload;
    }

    public boolean isWeaponCycle() {
        return weaponCycle;
    }
}
