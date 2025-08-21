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
                List.of(new Seat(true, null),
                        new Seat(false, new MountedWeapon(
                                WeaponFactory.getWeapon("Minigun"), // TODO
                                0.0 // Can rotate 360 degrees (handled differently)
                        ))));
        setPosition(new Vector2D(x, y));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Jeep movement: similar to tank but faster and more responsive
        double moveInput = input.getMoveY(); // Forward/backward
        double turnInput = input.getMoveX(); // Left/right turning

        // Only turn when moving (like a real vehicle)
        if (Math.abs(moveInput) > 0.01) {
            if (Math.abs(turnInput) > 0.01) {
                double angleChange = turnInput * turnSpeed * delta * Math.abs(moveInput);
                angle += angleChange;
                // Rotate the vehicle's vertices to match the new angle
                rotate(angleChange);
            }

            speed = moveInput * maxSpeed;
            velocityX = Math.cos(angle) * speed;
            velocityY = Math.sin(angle) * speed;
        } else {
            speed = 0;
            velocityX = 0;
            velocityY = 0;
        }
    }

    @Override
    public String getVehicleName() {
        return "Jeep";
    }
}
