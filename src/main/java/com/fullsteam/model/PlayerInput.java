package com.fullsteam.model;

public class PlayerInput {
    private double moveX; // Represents the horizontal axis (-1.0 for left, 1.0 for right)
    private double moveY; // Represents the vertical axis (-1.0 for up, 1.0 for down)

    private boolean shooting;
    private double mouseX;
    private double mouseY;
    private boolean reload;
    private boolean weaponCycle;

    public PlayerInput() {
    }

    // Getters and setters
    public double getMoveX() {
        return moveX;
    }

    public void setMoveX(double moveX) {
        this.moveX = moveX;
    }

    public double getMoveY() {
        return moveY;
    }

    public void setMoveY(double moveY) {
        this.moveY = moveY;
    }

    public boolean isShooting() {
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

    public void setWeaponCycle(boolean weaponCycle) {
        this.weaponCycle = weaponCycle;
    }
}