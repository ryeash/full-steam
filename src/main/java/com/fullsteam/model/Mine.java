package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

@Introspected
public final class Mine extends AbstractFieldEffect implements HasId, BulletEffect {

    @JsonIgnore
    private final long ownerId;
    private boolean triggered = false;

    public Mine(long id, int team, double x, double y, double triggerRadius, long expiration, long ownerId) {
        super(Type.MINE, id, x, y, triggerRadius, team, expiration);
        this.ownerId = ownerId;
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void markTriggered() {
        this.triggered = true;
    }

    @Override
    public boolean isExpired() {
        return triggered || super.isExpired();
    }

    public static Mine create(Bullet bullet, Object destructionSource) {
        return new Mine(Config.ID_COUNTER.incrementAndGet(),
                bullet.getTeam(),
                bullet.getX(),
                bullet.getY(),
                Config.PLAYER_RADIUS * 2,
                System.currentTimeMillis() + Config.MINE_DURATION_SECONDS,
                bullet.getShooterId());
    }

    public static Explosion mineExplosion(Mine mine) {
        return new Explosion(
                mine.getX(),
                mine.getY(),
                mine.getOwnerId(),
                mine.getTeam(),
                65,
                85,
                300);
    }
}
