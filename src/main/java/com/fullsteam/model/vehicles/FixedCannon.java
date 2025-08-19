package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class FixedCannon extends Vehicle {

    private static List<Vector2D> cannonVertices() {
        return List.of(
                new Vector2D(-Config.FIXED_CANNON_RADIUS, -Config.FIXED_CANNON_RADIUS),
                new Vector2D(Config.FIXED_CANNON_RADIUS, -Config.FIXED_CANNON_RADIUS),
                new Vector2D(Config.FIXED_CANNON_RADIUS, Config.FIXED_CANNON_RADIUS),
                new Vector2D(-Config.FIXED_CANNON_RADIUS, Config.FIXED_CANNON_RADIUS)
        );
    }

    public FixedCannon(double x, double y) {
        super(cannonVertices(), VehicleType.FIXED_CANNON,
                Config.FIXED_CANNON_HEALTH,   // Medium health
                0.0,                          // No movement (immobile)
                Config.FIXED_CANNON_TURN_SPEED, // Can rotate to aim
                0);                           // No passengers, operator only
        setPosition(new Vector2D(x, y));

        // High-damage rocket launcher
        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Rocket"),
                0.0 // Forward-facing cannon
        ));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        if (driverId == null) {
            return;
        }

        // Fixed cannon doesn't move, but can rotate to aim
        // Rotation is handled by aiming towards mouse
        double dx = input.getMouseX() - x;
        double dy = input.getMouseY() - y;
        if (dx != 0 || dy != 0) {
            angle = Math.atan2(dy, dx);
        }

        // Always stationary
        velocityX = 0;
        velocityY = 0;
        speed = 0;
    }

    @Override
    public boolean enterVehicle(Player player) {
        boolean entered = super.enterVehicle(player);
        if (entered && driverId != null && driverId.equals(player.id())) {
            // Operator controls the rocket launcher
            if (!mountedWeapons.isEmpty()) {
                mountedWeapons.getFirst().setControllerId(player.id());
            }
        }
        return entered;
    }

    @Override
    public String getVehicleName() {
        return "Fixed Cannon";
    }
}
