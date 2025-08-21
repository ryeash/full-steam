package com.fullsteam.model;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public abstract class Vehicle extends Obstacle implements HasLife, Targetable {
    protected final long id = Config.ID_COUNTER.incrementAndGet();
    protected double angle; // Vehicle rotation in radians
    protected double velocityX;
    protected double velocityY;
    protected double speed;
    protected double maxSpeed;
    protected double turnSpeed;
    protected double hp;
    protected double maxHp;
    protected boolean destroyed;
    protected List<Seat> seats;

    // Vehicle-specific properties
    protected VehicleType vehicleType;

    public enum VehicleType {
        TANK, MECH, JEEP, FIXED_CANNON
    }

    public static class Seat {
        public Player player;
        public final boolean driver;
        public final MountedWeapon mountedWeapon;

        public Seat(boolean driver, MountedWeapon mountedWeapon) {
            this.player = null;
            this.driver = driver;
            this.mountedWeapon = mountedWeapon;
        }
    }

    public static class MountedWeapon {
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
            while (angleDiff > Math.PI) angleDiff -= 2 * Math.PI;
            while (angleDiff < -Math.PI) angleDiff += 2 * Math.PI;
            
            // Constrain to maximum traverse range
            double halfTraverse = maximumRadian / 2.0;
            if (angleDiff > halfTraverse) {
                angleDiff = halfTraverse;
            } else if (angleDiff < -halfTraverse) {
                angleDiff = -halfTraverse;
            }
            
            return weaponDefaultAngle + angleDiff;
        }
    }

    public Vehicle(List<Vector2D> vertices, VehicleType vehicleType, double maxHp,
                   double maxSpeed, double turnSpeed, List<Seat> seats) {
        super(vertices, false);
        this.angle = 0;
        this.vehicleType = vehicleType;
        this.hp = maxHp;
        this.maxHp = maxHp;
        this.maxSpeed = maxSpeed;
        this.turnSpeed = turnSpeed;
        this.speed = 0;
        this.velocityX = 0;
        this.velocityY = 0;
        this.destroyed = false;
        this.seats = seats;
    }

    public void update(double deltaTime) {
        // Update position based on velocity (calculated from speed and angle)
        Vector2D center = getCenter();
        double worldBuffer = getBoundingRadius() / 2;
        double newX = CollisionUtils.constrain(center.x() + velocityX * deltaTime, worldBuffer, Config.GAME_WIDTH - worldBuffer);
        double newY = CollisionUtils.constrain(center.y() + velocityY * deltaTime, worldBuffer, Config.GAME_HEIGHT - worldBuffer);

        // Update position to new center
        setPosition(new Vector2D(newX, newY));

        // Check for mounted weapon reload completion
        for (Seat seat : seats) {
            MountedWeapon mountedWeapon = seat.mountedWeapon;
            if (mountedWeapon != null && mountedWeapon.isReloading() && System.currentTimeMillis() >= mountedWeapon.getReloadCompleteTime()) {
                mountedWeapon.finishReload();
            }
        }
    }

    @Override
    public Vector2D position() {
        return getCenter();
    }

    public boolean enterVehicle(Player player) {
        int team = getTeam();
        if (team < 0 || player.getTeam() == team) {
            for (Seat seat : seats) {
                if (seat.player == null) {
                    seat.player = player;
                    player.setVehicleId(id);
                    if (seat.mountedWeapon != null) {
                        seat.mountedWeapon.setControllerId(player.id());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean exitVehicle(Player player) {
        for (Seat seat : seats) {
            if (seat.player != null && seat.player.id() == player.id()) {
                player.setVehicleId(null);
                seat.player = null;
                if (seat.driver) {
                    velocityX = 0;
                    velocityY = 0;
                    speed = 0;
                }
                if (seat.mountedWeapon != null) {
                    seat.mountedWeapon.setControllerId(null);
                }
                return true;
            }
        }
        return false;
    }

    public boolean isPlayerInVehicle(Long playerId) {
        for (Seat seat : seats) {
            if (seat.player != null && seat.player.id() == playerId) {
                return true;
            }
        }
        return false;
    }

    public MountedWeapon getWeaponControlledBy(Long playerId) {
        for (Seat seat : seats) {
            if (seat.player != null && seat.player.id() == playerId) {
                return seat.mountedWeapon;
            }
        }
        return null;
    }

    // Abstract methods for vehicle-specific behavior
    public abstract void handleDriverInput(PlayerInput input, long delta);

    public abstract String getVehicleName();

    @Override
    public long id() {
        return id;
    }

    @Override
    public double getHp() {
        return hp;
    }

    @Override
    public void setHp(double hp) {
        this.hp = hp;
    }

    @Override
    public double getMaxHp() {
        return maxHp;
    }

    @Override
    public boolean takeDamage(double damage) {
        this.hp -= damage;
        if (this.hp <= 0) {
            this.destroyed = true;
            // Eject all passengers when destroyed
            for (Seat seat : seats) {
                if (seat.player != null) {
                    seat.player.setVehicleId(null);
                    seat.player = null;
                    if (seat.mountedWeapon != null) {
                        seat.mountedWeapon.setControllerId(null);
                    }
                }
            }
        }
        return this.hp <= 0;
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    public double getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(double velocityX) {
        this.velocityX = velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(double velocityY) {
        this.velocityY = velocityY;
    }

    public double getSpeed() {
        return speed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public double getTurnSpeed() {
        return turnSpeed;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public Long getDriverId() {
        return Optional.ofNullable(seats.getFirst())
                .map(s -> s.player)
                .map(Player::id)
                .orElse(null);
    }

    public int getTeam() {
        for (Seat seat : seats) {
            if (seat.player != null) {
                return seat.player.getTeam();
            }
        }
        return -1;
    }

    public List<Long> getPassengerIds() {
        return seats.stream().map(s -> s.player).filter(Objects::nonNull).map(Player::id).toList();
    }

    public int getMaxPassengers() {
        return seats.size();
    }

    public List<MountedWeapon> getMountedWeapons() {
        return seats.stream().map(s -> s.mountedWeapon).filter(Objects::nonNull).toList();
    }

    @Override
    public void rotate(double angleRadians) {
        super.rotate(angleRadians);
        // Rotate mounted weapons around the vehicle's center
        Vector2D center = getCenter();
        for (Seat seat : seats) {
            MountedWeapon mountedWeapon = seat.mountedWeapon;
            if (mountedWeapon != null) {
                Vector2D weaponPos = mountedWeapon.position;
                // Translate weapon position to origin
                double dx = weaponPos.x() - center.x();
                double dy = weaponPos.y() - center.y();
                // Rotate around center
                double cos = Math.cos(angleRadians);
                double sin = Math.sin(angleRadians);
                double newX = dx * cos - dy * sin + center.x();
                double newY = dx * sin + dy * cos + center.y();
                // Update weapon position
                mountedWeapon.position = new Vector2D(newX, newY);
            }
        }
    }

    @Override
    public void setPosition(Vector2D position) {
        // update the position of the weapons
        Vector2D center = getCenter();
        for (Seat seat : seats) {
            MountedWeapon mountedWeapon = seat.mountedWeapon;
            if (mountedWeapon != null) {
                double offsetX = position.x() - center.x();
                double offsetY = position.y() - center.y();
                // Translate weapon position to be relative to the new center
                double dx = mountedWeapon.position.x() + offsetX;
                double dy = mountedWeapon.position.y() + offsetY;
                // Update weapon position to new center
                mountedWeapon.position = new Vector2D(dx, dy);
            }
        }
        super.setPosition(position);
    }

    public void cycleSeats(Player player) {
        for (int i = 0; i < seats.size(); i++) {
            Seat currentSeat = seats.get(i);
            if (currentSeat.player != null && currentSeat.player.id() == player.id()) {
                // Find the next available seat
                for (int j = 1; j < seats.size(); j++) {
                    int nextIndex = (i + j) % seats.size();
                    Seat nextSeat = seats.get(nextIndex);
                    if (nextSeat.player == null) {
                        // Move player to the next seat
                        nextSeat.player = player;
                        currentSeat.player = null;
                        if (currentSeat.mountedWeapon != null) {
                            currentSeat.mountedWeapon.setControllerId(null);
                        }
                        if (nextSeat.mountedWeapon != null) {
                            nextSeat.mountedWeapon.setControllerId(player.id());
                        }
                        return;
                    }
                }
                break; // No available seats found
            }
        }
    }
}
