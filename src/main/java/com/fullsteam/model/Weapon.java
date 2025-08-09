package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.function.Function;

/**
 * Represents a weapon with customizable stats based on a point system.
 * Each stat's effectiveness is determined by the number of points allocated to it,
 * with a total cap to ensure balanced weapon design.
 */
public class Weapon {

    public static final int MAX_TOTAL_POINTS = 100;

    // Weapon Stats
    private final String name;
    private final String shortName;
    @JsonIgnore
    private final long fireRateCooldown; // Cooldown in ms, lower is faster
    @JsonIgnore
    private final double bulletDamage;
    @JsonIgnore
    private final double bulletRange;
    @JsonIgnore
    private final double bulletSpeed;
    @JsonIgnore
    private final double bulletSpeedDecay; // Multiplier per tick, closer to 1.0 is better
    @JsonIgnore
    private final double bulletSpread; // Inaccuracy in radians, lower is better
    @JsonIgnore
    private final int bulletsPerShot; // For shotgun-style weapons
    @JsonIgnore
    private final int roundsPerMagazine; // Number of shots before reloading
    @JsonIgnore
    private final long reloadTime; // Time in ms to reload
    @JsonIgnore
    private final Function<Bullet, BulletEffect> onBulletDestruction;

    /**
     * Creates a new Weapon by converting stat points into game values.
     *
     * @param name               The name of the weapon preset (e.g., "Assault").
     * @param fireRatePoints     Points for fire rate (0-30). More points = lower cooldown.
     * @param damagePoints       Points for damage (0-40). More points = more damage.
     * @param rangePoints        Points for range (0-10). More points = longer bullet travel distance.
     * @param speedPoints        Points for bullet speed (0-10). More points = faster bullets.
     * @param speedDecayPoints   Points for speed decay (0-10). More points = less speed loss over time.
     * @param accuracyPoints     Points for accuracy (0-10). More points = less bullet spread.
     * @param multiShotPoints    Points for multi-shot. More points = more bullets per shot.
     * @param magazineSizePoints Points for magazine size. More points = more rounds.
     * @param reloadSpeedPoints  Points for reload speed. More points = less reload time.
     */
    public Weapon(String name, String shortName, int fireRatePoints, int damagePoints, int rangePoints, int speedPoints, int speedDecayPoints, int accuracyPoints, int multiShotPoints, int magazineSizePoints, int reloadSpeedPoints, Function<Bullet, BulletEffect> onBulletDestruction) {
        this.name = name;
        this.shortName = shortName;

        int totalPoints = fireRatePoints + damagePoints + rangePoints + speedPoints + speedDecayPoints + accuracyPoints + multiShotPoints + magazineSizePoints + reloadSpeedPoints + (onBulletDestruction == null ? 0 : 60);
        if (totalPoints > MAX_TOTAL_POINTS) {
            System.out.println("over allocated: " + name + " " + totalPoints);
            throw new IllegalArgumentException(
                    String.format("Weapon '%s' exceeds max points. Has %d, max is %d.", name, totalPoints, MAX_TOTAL_POINTS)
            );
        }
        if (totalPoints < MAX_TOTAL_POINTS) {
            System.out.println(name + " is not using all customization points; leftover: " + (MAX_TOTAL_POINTS - totalPoints));
        }

        // --- Convert points to actual stats ---

        // Fire Rate (Cooldown in ms): Base 1000ms. Each point reduces cooldown by 25ms.
        this.fireRateCooldown = 1000 - (fireRatePoints * 25L);

        // Damage: Base 10. Each point adds 2 damage.
        this.bulletDamage = 10 + (damagePoints * 2);

        // Range: Base 100 units. Each point adds 50 units.
        this.bulletRange = 100 + (rangePoints * 50);

        // Bullet Speed: Base 3.0 units/tick. Each point adds 0.2.
        this.bulletSpeed = .2 + (speedPoints * 0.02);

        // Bullet Speed Decay: Base 0.98 (2% decay). Each point adds 0.002, up to 1.0 (no decay).
        this.bulletSpeedDecay = 0.98 + (speedDecayPoints * 0.002);

        // Accuracy (Spread in radians): Base 0.5. Each point reduces spread by 0.015.
        this.bulletSpread = Math.max(0.0, 0.75 - (accuracyPoints * 0.05));

        // Multi-shot: Base 1 bullet. Every 15 points adds an additional bullet.
        this.bulletsPerShot = 1 + (multiShotPoints / 10);

        // Magazine Size: Base 6 rounds. Each point adds 3 rounds.
        this.roundsPerMagazine = Math.max(1, 6 + (magazineSizePoints * 3));

        // Reload Time (ms): Base 5000ms. Each point reduces time by 200ms. Minimum of 500ms.
        this.reloadTime = Math.max(500L, 4000L - (reloadSpeedPoints * 200L));

        // TODO: fix the points for destruction
        this.onBulletDestruction = onBulletDestruction;
    }

    public String getName() {
        return name;
    }

    public String getShortName() {
        return shortName;
    }

    public long getFireRateCooldown() {
        return fireRateCooldown;
    }

    public double getBulletDamage() {
        return bulletDamage;
    }

    public double getBulletRange() {
        return bulletRange;
    }

    public double getBulletSpeed() {
        return bulletSpeed;
    }

    public double getBulletSpeedDecay() {
        return bulletSpeedDecay;
    }

    public double getBulletSpread() {
        return bulletSpread;
    }

    public int getBulletsPerShot() {
        return bulletsPerShot;
    }

    public int getRoundsPerMagazine() {
        return roundsPerMagazine;
    }

    public long getReloadTime() {
        return reloadTime;
    }

    public Function<Bullet, BulletEffect> getOnBulletDestruction() {
        return onBulletDestruction;
    }
}