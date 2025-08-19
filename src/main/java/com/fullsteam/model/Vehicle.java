package com.fullsteam.model;

import com.fullsteam.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Vehicle extends Obstacle implements HasId, HasLife, Targetable {
    protected final long id = Config.ID_COUNTER.incrementAndGet();
    protected double x;
    protected double y;
    protected double angle; // Vehicle rotation in radians
    protected double velocityX;
    protected double velocityY;
    protected double speed;
    protected double maxSpeed;
    protected double turnSpeed;
    protected double hp;
    protected double maxHp;
    protected boolean destroyed;
    protected long lastPlayerSwap = System.currentTimeMillis();

    // Driver and passenger system
    protected Long driverId; // Player ID of the driver
    protected int team;
    protected List<Long> passengerIds; // Player IDs of passengers
    protected int maxPassengers;

    // Mounted weapons for passengers
    protected List<MountedWeapon> mountedWeapons;

    // Vehicle-specific properties
    protected VehicleType vehicleType;

    public enum VehicleType {
        TANK, MECH, JEEP, FIXED_CANNON
    }

    public static class MountedWeapon {
        private final Weapon weapon;
        private final double mountAngleOffset; // Relative to vehicle angle
        private Long controllerId;
        private int currentAmmo;
        private boolean reloading;
        private long reloadCompleteTime;
        private long nextShotTime;

        public MountedWeapon(Weapon weapon, double mountAngleOffset) {
            this.weapon = weapon;
            this.mountAngleOffset = mountAngleOffset;
            this.controllerId = null;
            this.currentAmmo = weapon.getRoundsPerMagazine();
            this.reloading = false;
            this.reloadCompleteTime = 0;
            this.nextShotTime = 0;
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

        // Getters
        public Weapon getWeapon() {
            return weapon;
        }

        public double getMountAngleOffset() {
            return mountAngleOffset;
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
    }

    public Vehicle(List<Vector2D> vertices, VehicleType vehicleType, double maxHp,
                   double maxSpeed, double turnSpeed, int maxPassengers) {
        super(vertices, false);
        this.angle = 0;
        this.vehicleType = vehicleType;
        this.hp = maxHp;
        this.maxHp = maxHp;
        this.maxSpeed = maxSpeed;
        this.turnSpeed = turnSpeed;
        this.maxPassengers = maxPassengers;
        this.speed = 0;
        this.velocityX = 0;
        this.velocityY = 0;
        this.destroyed = false;
        this.driverId = null;
        this.passengerIds = new ArrayList<>();
        this.mountedWeapons = new ArrayList<>();
    }

    public void update(double deltaTime) {
        // Update position based on speed and angle
        Vector2D center = getCenter();
        double newX = center.x() + Math.cos(angle) * speed * deltaTime;
        double newY = center.y() + Math.sin(angle) * speed * deltaTime;

        // Calculate translation vector
        double dx = newX - center.x();
        double dy = newY - center.y();

        // Translate all vertices
        for (int i = 0; i < getVertices().size(); i++) {
            Vector2D v = getVertices().get(i);
            getVertices().set(i, new Vector2D(v.x() + dx, v.y() + dy));
        }

        // Update rotation
//        rotate(angle * deltaTime);
    }

    public boolean hasDriver() {
        return driverId != null;
    }

    public boolean hasRoom() {
        return passengerIds.size() < maxPassengers;
    }

    public boolean enterVehicle(Player player) {
        if (lastPlayerSwap + 500 > System.currentTimeMillis()) {
            return false;
        }
        lastPlayerSwap = System.currentTimeMillis();
        if (!hasDriver()) {
            driverId = player.id();
            player.setVehicleId(id);
            team = player.getTeam();
            return true;
        } else if (hasRoom() && team == player.getTeam()) {
            passengerIds.add(player.id());
            player.setVehicleId(id);
            // Assign to first available mounted weapon
            for (MountedWeapon weapon : mountedWeapons) {
                if (weapon.getControllerId() == null) {
                    weapon.setControllerId(player.id());
                    break;
                }
            }
            return true;
        }
        return false;
    }

    public boolean exitVehicle(Player player) {
        if (lastPlayerSwap + 500 < System.currentTimeMillis()) {
            return false;
        }
        lastPlayerSwap = System.currentTimeMillis();
        if (player.getVehicleId() == id) {
            player.setVehicleId(null);
        } else {
            return false;
        }
        if (driverId != null && driverId.equals(player.id())) {
            driverId = null;
            team = -1;
            // Stop the vehicle when driver exits
            velocityX = 0;
            velocityY = 0;
            speed = 0;
            return true;
        } else if (passengerIds.remove(player.id())) {
            // Remove from weapon control
            for (MountedWeapon weapon : mountedWeapons) {
                if (Objects.equals(player.id(), weapon.getControllerId())) {
                    weapon.setControllerId(null);
                    break;
                }
            }
            return true;
        }
        return false;
    }

    public boolean isPlayerInVehicle(Long playerId) {
        return (driverId != null && driverId.equals(playerId)) || passengerIds.contains(playerId);
    }

    public MountedWeapon getWeaponControlledBy(Long playerId) {
        for (MountedWeapon weapon : mountedWeapons) {
            if (Objects.equals(playerId, weapon.getControllerId())) {
                return weapon;
            }
        }
        return null;
    }

    // Abstract methods for vehicle-specific behavior
    public abstract void handleDriverInput(PlayerInput input, long delta);

    public abstract String getVehicleName();

    // Implementations of HasId, HasLife, Targetable interfaces
    @Override
    public long getId() {
        return id;
    }

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
            driverId = null;
            team = -1;
            passengerIds.clear();
            for (MountedWeapon weapon : mountedWeapons) {
                weapon.setControllerId(null);
            }
        }
        return this.hp <= 0;
    }

    @Override
    public Vector2D position() {
        return new Vector2D(x, y);
    }

    // Getters and setters
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
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
        return driverId;
    }

    public int getTeam() {
        return team;
    }

    public List<Long> getPassengerIds() {
        return passengerIds;
    }

    public List<MountedWeapon> getMountedWeapons() {
        return mountedWeapons;
    }

    public int getMaxPassengers() {
        return maxPassengers;
    }
}
