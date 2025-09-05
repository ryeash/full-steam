package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;

@Introspected
public final class Portal extends AbstractFieldEffect implements BulletEffect {

    @JsonIgnore
    private final long ownerId;
    @JsonIgnore
    private long linkedTo;

    public Portal(long id, int team, double x, double y, double radius, long ownerId) {
        super(Type.PORTAL, id, x, y, radius, team, 0L);
        this.ownerId = ownerId;
        this.linkedTo = -1;
    }

    public long getOwnerId() {
        return ownerId;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    /**
     * Creates a new portal from a bullet impact
     */
//    public static Portal create(Bullet bullet, Object destructionSource) {
//        return new Portal(
//                Config.ID_COUNTER.incrementAndGet(),
//                bullet.getTeam(),
//                bullet.getX(),
//                bullet.getY(),
//                Config.PLAYER_RADIUS * 3.0, // bigger than player
//                System.currentTimeMillis() + Config.PORTAL_DURATION_MS,
//                bullet.getShooterId()
//        );
//    }

    /**
     * Calculates the exit position for a bullet teleporting through this portal
     * The bullet exits from the linked portal in a mirrored position (left becomes right, etc.)
     */
    public Vector2D calculateExitPosition(Vector2D entryPosition, Portal linkedPortal) {
        if (linkedPortal == null) {
            return entryPosition; // No teleportation if no linked portal
        }
        // Calculate the offset from this portal's center to the entry position
        Vector2D offset = entryPosition.subtract(this.position());
        // Mirror the offset (multiply by -1 to flip to opposite side)
        Vector2D mirroredOffset = offset.multiply(-1);
        // Apply the mirrored offset to the linked portal's position
        return linkedPortal.position().add(mirroredOffset);
    }

    /**
     * Checks if a position is within the portal's teleportation radius
     */
    public boolean isWithinTeleportRadius(Vector2D position) {
        return position.distanceSquared(this.position()) <= getRadiusSquared();
    }

    @Override
    public Vector2D position() {
        return new Vector2D(getX(), getY());
    }

    public long getLinkedTo() {
        return linkedTo;
    }

    public void setLinkedTo(long linkedTo) {
        this.linkedTo = linkedTo;
    }
}
