package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

import java.util.Objects;

@Introspected
public class PlayerInput {
    private double moveX;
    private double moveY;
    private boolean fire;
    private boolean altFire; // e.g. vehicle secondary weapon
    private double mouseX;
    private double mouseY;
    private boolean reload;
    public boolean action1; // e.g. placing a crate
    private boolean action2; // e.g. enter and exit vehicle

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

    public boolean isFire() {
        return fire;
    }

    public void setFire(boolean fire) {
        this.fire = fire;
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

    public boolean isAction1() {
        return action1;
    }

    public void setAction1(boolean action1) {
        this.action1 = action1;
    }

    public boolean isAction2() {
        return action2;
    }

    public void setAction2(boolean action2) {
        this.action2 = action2;
    }

    public boolean isAltFire() {
        return altFire;
    }

    public void setAltFire(boolean altFire) {
        this.altFire = altFire;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PlayerInput that = (PlayerInput) o;
        return Double.compare(moveX, that.moveX) == 0
                && Double.compare(moveY, that.moveY) == 0
                && fire == that.fire
                && Double.compare(mouseX, that.mouseX) == 0
                && Double.compare(mouseY, that.mouseY) == 0
                && reload == that.reload
                && action1 == that.action1
                && action2 == that.action2
                && altFire == that.altFire;
    }

    @Override
    public int hashCode() {
        return Objects.hash(moveX, moveY, fire, mouseX, mouseY, reload, action1, action2, altFire);
    }
}