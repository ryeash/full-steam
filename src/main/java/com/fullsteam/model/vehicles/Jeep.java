package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.MountedWeapon;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class Jeep extends Vehicle {

    public static List<Vector2D> jeepVertices() {
        // Create a jeep that's longer than it is wide (like a real jeep)
        // Front of jeep faces positive X direction (right) when angle = 0
        double halfWidth = Config.JEEP_WIDTH / 2;   // Shorter dimension (side to side)
        double halfLength = Config.JEEP_LENGTH / 2; // Longer dimension (front to back)

        return List.of(
                // Front of jeep (narrow end, pointing right/positive X when angle = 0)
                new Vector2D(halfLength, -halfWidth * 0.8),   // Front right
                new Vector2D(halfLength, halfWidth * 0.8),    // Front left
                // Rear of jeep (wider end)
                new Vector2D(-halfLength, halfWidth),         // Rear left
                new Vector2D(-halfLength, -halfWidth)         // Rear right
        );
    }

    public Jeep() {
        super(jeepVertices(),
                VehicleType.JEEP,
                Config.JEEP_HEALTH,      // Low health
                Config.JEEP_MAX_SPEED,   // Fast movement
                Config.JEEP_TURN_SPEED,  // Medium turning
                List.of(new Seat(true, null), // Driver has no weapon
                        new Seat(false, new MountedWeapon(
                                new Vector2D(-Config.JEEP_LENGTH / 4, 0), // Mount at rear center
                                WeaponFactory.getWeapon("Minigun"),
                                0, // Default angle (rear-facing)
                                Math.PI * 4/3, // ±120° traverse range (wide coverage)
                                1.0
                        ))));
        setPosition(new Vector2D(0, 0));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Jeep movement: similar to tank but faster and more responsive
        double moveInput = -input.getMoveY(); // Forward/backward
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
