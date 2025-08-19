package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class Jeep extends Vehicle {

    public static List<Vector2D> jeepVertices() {
        return List.of(
                new Vector2D(-Config.JEEP_WIDTH / 2, -Config.JEEP_LENGTH / 2),
                new Vector2D(Config.JEEP_WIDTH / 2, -Config.JEEP_LENGTH / 2),
                new Vector2D(Config.JEEP_WIDTH / 2, Config.JEEP_LENGTH / 2),
                new Vector2D(-Config.JEEP_WIDTH / 2, Config.JEEP_LENGTH / 2)
        );
    }

    public Jeep(double x, double y) {
        super(jeepVertices(),
                VehicleType.JEEP,
                Config.JEEP_HEALTH,      // Low health
                Config.JEEP_MAX_SPEED,   // Fast movement
                Config.JEEP_TURN_SPEED,  // Medium turning
                1);                      // 1 passenger + driver = 2 total
        setPosition(new Vector2D(x, y));

        // Roof-mounted minigun for passenger
        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Minigun"), // TODO
                0.0 // Can rotate 360 degrees (handled differently)
        ));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        if (driverId == null) return;

        // Jeep movement: similar to tank but faster and more responsive
        double moveInput = input.getMoveY(); // Forward/backward
        double turnInput = input.getMoveX(); // Left/right turning

        // Only turn when moving (like a real vehicle)
        if (Math.abs(moveInput) > 0.01) {
            if (Math.abs(turnInput) > 0.01) {
                angle += turnInput * turnSpeed * delta * Math.abs(moveInput);
            }

            speed = moveInput * maxSpeed;
            velocityX = Math.cos(angle) * speed;
            velocityY = Math.sin(angle) * speed;
        } else {
            speed = 0;
            velocityX = 0;
            velocityY = 0;
        }

        // Driver doesn't control weapons in jeep, only movement
    }

    @Override
    public boolean enterVehicle(Player player) {
        boolean entered = super.enterVehicle(player);
        if (entered) {
            // Only passengers control the minigun, not the driver
            if (!driverId.equals(player.id()) && !mountedWeapons.isEmpty()) {
                mountedWeapons.getFirst().setControllerId(player.id());
            }
        }
        return entered;
    }

    @Override
    public String getVehicleName() {
        return "Jeep";
    }
}
