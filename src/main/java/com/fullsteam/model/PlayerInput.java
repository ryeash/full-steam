package com.fullsteam.model;

import java.util.Objects;

public class PlayerInput {
    private double moveX;
    private double moveY;
    private boolean shooting;
    private double mouseX;
    private double mouseY;
    private boolean reload;
    public boolean placingObstacle;
    
    // Vehicle controls
    private boolean enterExitVehicle;
    private boolean vehicleSecondaryFire; // For passengers controlling mounted weapons

    public PlayerInput() {
    }

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

    public boolean isPlacingObstacle() {
        return placingObstacle;
    }

    public void setPlacingObstacle(boolean placingObstacle) {
        this.placingObstacle = placingObstacle;
    }

    public boolean isEnterExitVehicle() {
        return enterExitVehicle;
    }

    public void setEnterExitVehicle(boolean enterExitVehicle) {
        this.enterExitVehicle = enterExitVehicle;
    }

    public boolean isVehicleSecondaryFire() {
        return vehicleSecondaryFire;
    }

    public void setVehicleSecondaryFire(boolean vehicleSecondaryFire) {
        this.vehicleSecondaryFire = vehicleSecondaryFire;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlayerInput that = (PlayerInput) o;
        return Double.compare(moveX, that.moveX) == 0
               && Double.compare(moveY, that.moveY) == 0
               && shooting == that.shooting
               && Double.compare(mouseX, that.mouseX) == 0
               && Double.compare(mouseY, that.mouseY) == 0
               && reload == that.reload
               && placingObstacle == that.placingObstacle
               && enterExitVehicle == that.enterExitVehicle
               && vehicleSecondaryFire == that.vehicleSecondaryFire;
    }

    @Override
    public int hashCode() {
        return Objects.hash(moveX, moveY, shooting, mouseX, mouseY, reload, placingObstacle, enterExitVehicle, vehicleSecondaryFire);
    }
}