package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import io.micronaut.core.annotation.Introspected;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Introspected
public abstract class Vehicle extends Obstacle implements HasLife, Targetable {

    public enum VehicleType {
        JEEP,
        DAVINCI,
        FIXED_CANNON,
        MECH,
        TANK,
    }

    protected final long id = Config.ID_COUNTER.incrementAndGet();
    protected double angle; // Vehicle rotation in radians
    @JsonIgnore
    protected double velocityX;
    @JsonIgnore
    protected double velocityY;
    @JsonIgnore
    protected double speed;
    @JsonIgnore
    protected double maxSpeed;
    @JsonIgnore
    protected double turnSpeed;
    protected double hp;
    protected double maxHp;
    @JsonIgnore
    protected List<Seat> seats;
    protected VehicleType vehicleType;

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

    public double getX() {
        return position().x();
    }

    public double getY() {
        return position().y();
    }

    public double getRadius() {
        return getBoundingRadius();
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

    public void clearSeats() {
        for (Seat seat : seats) {
            seat.player = null;
            if (seat.mountedWeapon != null) {
                seat.mountedWeapon.setControllerId(null);
            }
        }
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

    /**
     * Heals the vehicle by the specified amount.
     * Will not heal beyond maxHp.
     *
     * @param amount The amount to heal
     */
    public void heal(double amount) {
        if (this.hp > 0 && amount > 0) {
            this.hp = Math.min(maxHp, this.hp + amount);
        }
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

    @JsonIgnore
    public boolean isDestroyed() {
        return hp <= 0;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public Long getDriverId() {
        return Optional.ofNullable(seats.isEmpty() ? null : seats.getFirst())
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
                Vector2D weaponPos = mountedWeapon.position();
                // Translate weapon position to origin
                double dx = weaponPos.x() - center.x();
                double dy = weaponPos.y() - center.y();
                // Rotate around center
                double cos = Math.cos(angleRadians);
                double sin = Math.sin(angleRadians);
                double newX = dx * cos - dy * sin + center.x();
                double newY = dx * sin + dy * cos + center.y();
                // Update weapon position
                mountedWeapon.setPosition(new Vector2D(newX, newY));
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
                double dx = mountedWeapon.position().x() + offsetX;
                double dy = mountedWeapon.position().y() + offsetY;
                // Update weapon position to new center
                mountedWeapon.setPosition(new Vector2D(dx, dy));
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
