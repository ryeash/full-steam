package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class Mech extends Vehicle {

    static List<Vector2D> mechVertices() {
        double halfWidth = Config.MECH_WIDTH / 2;
        double halfHeight = Config.MECH_HEIGHT / 2;

        return List.of(
                // 6-point hexagon mech (facing positive X when angle = 0)
                // Start from front point and go clockwise
                new Vector2D(halfWidth, 0),                          // 1. Front point
                new Vector2D(halfWidth * 0.3, halfHeight),           // 2. Front-right
                new Vector2D(-halfWidth * 0.3, halfHeight),          // 3. Rear-right
                new Vector2D(-halfWidth, 0),                         // 4. Rear point
                new Vector2D(-halfWidth * 0.3, -halfHeight),         // 5. Rear-left
                new Vector2D(halfWidth * 0.3, -halfHeight)           // 6. Front-left
        );
    }

    public Mech() {
        super(mechVertices(), VehicleType.MECH,
                Config.MECH_HEALTH,      // Medium health
                Config.MECH_MAX_SPEED,   // Medium speed
                Config.MECH_TURN_SPEED,  // Fast turning
                List.of(new Seat(true, new MountedWeapon(
                                new Vector2D(0, Config.MECH_HEIGHT / 2), // Driver controlled right arm
                                WeaponFactory.getWeapon("Laser Minigun"),
                                Math.PI / 8, // Default angle (forward)
                                Math.PI - Math.PI / 8, // ±90° traverse range (front hemisphere)
                                3.0
                        )),
                        new Seat(false, new MountedWeapon(
                                new Vector2D(0, -Config.MECH_HEIGHT / 2), // Passenger controller left arm
                                WeaponFactory.getWeapon("Laser Minigun"),
                                -Math.PI / 8, // Default angle (rear)
                                Math.PI - Math.PI / 8, // ±90° traverse range (rear hemisphere)
                                3.0
                        ))
                ));

        setPosition(new Vector2D(0, 0));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Mech movement: tank-like controls (forward/backward with turning)
        double moveInput = -input.getMoveY(); // Forward/backward (inversion needed for mech)
        double turnInput = input.getMoveX(); // Left/right turning

        // Apply turning and rotate the vehicle geometry
        if (Math.abs(turnInput) > 0.01) {
            double angleChange = turnInput * turnSpeed * delta;
            angle += angleChange;
            // Rotate the vehicle's vertices to match the new angle
            rotate(angleChange);
        }

        // Apply movement in the direction the mech is facing
        if (Math.abs(moveInput) > 0.01) {
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
        return "Mech";
    }
}
