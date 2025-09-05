package com.fullsteam.model;

import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A field effect that creates scattered bullets in random directions
 * Used by the Scatterblast grenade weapon
 */
@Introspected
public final class BulletScatter extends AbstractFieldEffect implements BulletEffect {

    private final long shooterId;
    private final double bulletDamage;
    private final double bulletSpeed;
    private final double bulletRange;
    private final int bulletCount;

    public BulletScatter(long id, int team, double x, double y, double radius, long expiration,
                         long shooterId, double bulletDamage, double bulletSpeed, double bulletRange, int bulletCount) {
        super(Type.BULLET_SCATTER, id, x, y, radius, team, expiration);
        this.shooterId = shooterId;
        this.bulletDamage = bulletDamage;
        this.bulletSpeed = bulletSpeed;
        this.bulletRange = bulletRange;
        this.bulletCount = bulletCount;
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getBulletDamage() {
        return bulletDamage;
    }

    public double getBulletSpeed() {
        return bulletSpeed;
    }

    public double getBulletRange() {
        return bulletRange;
    }

    public int getBulletCount() {
        return bulletCount;
    }

    /**
     * Creates a BulletScatter effect from a bullet destruction
     */
    public static BulletScatter create(Bullet bullet, Object destructionSource) {
        // Random bullet count between 3-7
        int bulletCount = ThreadLocalRandom.current().nextInt(3, 8);

        return new BulletScatter(
                Config.ID_COUNTER.incrementAndGet(),
                bullet.getTeam(),
                bullet.getX(),
                bullet.getY(),
                15.0, // Small radius for visual effect
                System.currentTimeMillis() + 100, // Very short duration, just for processing
                bullet.getShooterId(),
                25.0, // Medium damage
                180.0, // Moderate speed
                120.0, // Short range
                bulletCount
        );
    }
}
