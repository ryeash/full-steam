package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public final class MountedWeapon {
    private Vector2D position; // absolute position of the weapon mount
    private final Weapon weapon;
    private final double defaultAngle; // default direction relative to vehicle (0 = forward)
    private final double maximumRadian; // max traverse range in radians (e.g., Math.PI/2 = 90°)
    private Long controllerId;
    private int currentAmmo;
    private boolean reloading;
    private long reloadCompleteTime;
    private long nextShotTime;
    private final double damageModification;

    public MountedWeapon(Vector2D position, Weapon weapon, double defaultAngle, double maximumRadian, double damageModification) {
        this.position = position;
        this.weapon = weapon;
        this.defaultAngle = defaultAngle;
        this.maximumRadian = maximumRadian;
        this.damageModification = damageModification;
        this.controllerId = null;
        this.currentAmmo = weapon.getRoundsPerMagazine();
        this.reloading = false;
        this.reloadCompleteTime = 0;
        this.nextShotTime = 0;
    }

    public Vector2D position() {
        return position;
    }

    public void setPosition(Vector2D position) {
        this.position = position;
    }

    public double getX() {
        return position.x();
    }

    public double getY() {
        return position.y();
    }

    public boolean canShoot() {
        return !reloading
               && currentAmmo > 0
               && System.currentTimeMillis() >= nextShotTime;
    }

    public void shoot() {
        if (!canShoot()) {
            return;
        }
        this.nextShotTime = System.currentTimeMillis() + weapon.getFireRateCooldown();
        this.currentAmmo -= weapon.getBulletsPerShot();
    }

    public void startReload() {
        if (reloading || currentAmmo == weapon.getRoundsPerMagazine()) {
            return;
        }
        this.reloading = true;
        this.reloadCompleteTime = System.currentTimeMillis() + weapon.getReloadTime();
    }

    public void finishReload() {
        this.reloading = false;
        this.currentAmmo = weapon.getRoundsPerMagazine();
    }

    public Weapon getWeapon() {
        return weapon;
    }

    public double getDefaultAngle() {
        return defaultAngle;
    }

    public double getMaximumRadian() {
        return maximumRadian;
    }

    public Long getControllerId() {
        return controllerId;
    }

    public int getCurrentAmmo() {
        return currentAmmo;
    }

    public boolean isReloading() {
        return reloading;
    }

    public long getReloadCompleteTime() {
        return reloadCompleteTime;
    }

    public long getNextShotTime() {
        return nextShotTime;
    }

    public void setControllerId(Long controllerId) {
        this.controllerId = controllerId;
    }

    public double getDamageModification() {
        return damageModification;
    }

    /**
     * Calculates the constrained weapon angle based on traverse limits.
     *
     * @param desiredAngle The angle the player wants to aim at
     * @param vehicleAngle The current angle of the vehicle
     * @return The constrained angle within traverse limits
     */
    public double getConstrainedAngle(double desiredAngle, double vehicleAngle) {
        if (maximumRadian >= Math.PI * 2) {
            // Full 360° traverse - no constraints
            return desiredAngle;
        }

        // Calculate the weapon's default direction in world coordinates
        double weaponDefaultAngle = vehicleAngle + defaultAngle;

        // Calculate the difference between desired and default angles
        double angleDiff = desiredAngle - weaponDefaultAngle;

        // Normalize angle difference to be between -π and π
        while (angleDiff > Math.PI){
            angleDiff -= 2 * Math.PI;
        }
        while (angleDiff < -Math.PI) {
            angleDiff += 2 * Math.PI;
        }

        // Constrain to maximum traverse range
        double halfTraverse = maximumRadian / 2.0;
        if (angleDiff > halfTraverse) {
            angleDiff = halfTraverse;
        } else if (angleDiff < -halfTraverse) {
            angleDiff = -halfTraverse;
        }

        double finalAngle = weaponDefaultAngle + angleDiff;
        
        // Normalize final angle to [0, 2π] range
        while (finalAngle < 0) {
            finalAngle += 2 * Math.PI;
        }
        while (finalAngle >= 2 * Math.PI) {
            finalAngle -= 2 * Math.PI;
        }
        
        return finalAngle;
    }
}
