package com.fullsteam.model;

import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

import java.util.concurrent.ThreadLocalRandom;

import static com.fullsteam.Config.LASER_SHOT_DURATION;

/**
 * A field effect that creates scattered laser blasts in random directions
 * Used by the Laser Scatterblast grenade weapon
 */
@Introspected
public final class LaserScatter extends AbstractFieldEffect implements BulletEffect {

    private final long shooterId;
    private final double laserDamage;
    private final double laserRange;
    private final int laserCount;

    public LaserScatter(long id, int team, double x, double y, double radius, long expiration,
                        long shooterId, double laserDamage, double laserRange, int laserCount) {
        super(FieldEffect.Type.LASER_SCATTER, id, x, y, radius, team, expiration);
        this.shooterId = shooterId;
        this.laserDamage = laserDamage;
        this.laserRange = laserRange;
        this.laserCount = laserCount;
    }

    public long getShooterId() {
        return shooterId;
    }

    public double getLaserDamage() {
        return laserDamage;
    }

    public double getLaserRange() {
        return laserRange;
    }

    public int getLaserCount() {
        return laserCount;
    }

    /**
     * Creates a LaserScatter effect from a bullet destruction
     */
    public static LaserScatter create(Bullet bullet, Object destructionSource) {
        int laserCount = ThreadLocalRandom.current().nextInt(3, 6);
        return new LaserScatter(
                Config.ID_COUNTER.incrementAndGet(),
                bullet.getTeam(),
                bullet.getX(),
                bullet.getY(),
                20.0, // Slightly larger radius for visual effect
                System.currentTimeMillis() + LASER_SHOT_DURATION, // just for processing
                bullet.getShooterId(),
                15.0,
                135.0, // Longer range than bullet scatter
                laserCount
        );
    }
}
