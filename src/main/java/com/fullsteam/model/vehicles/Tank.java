package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.MountedWeapon;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class Tank extends Vehicle {

    static List<Vector2D> tankVertices() {
        // Create a tank-shaped polygon that faces forward (positive X direction)
        // The tank should be longer than it is wide, with the front being the narrow end
        double halfWidth = Config.TANK_WIDTH / 2;
        double halfLength = Config.TANK_LENGTH / 2;

        return List.of(
                // Front of tank (narrow end, pointing right/positive X when angle = 0)
                new Vector2D(halfLength, -halfWidth * 0.6),      // Front-right
                new Vector2D(halfLength, halfWidth * 0.6),       // Front-left
                // Sides of tank
                new Vector2D(halfLength * 0.3, halfWidth),       // Mid-front left
                new Vector2D(-halfLength * 0.3, halfWidth),      // Mid-rear left
                // Rear of tank (wide end)
                new Vector2D(-halfLength, halfWidth),            // Rear-left
                new Vector2D(-halfLength, -halfWidth),           // Rear-right
                // Back to front on right side
                new Vector2D(-halfLength * 0.3, -halfWidth),     // Mid-rear right
                new Vector2D(halfLength * 0.3, -halfWidth)       // Mid-front right
        );
    }

    public Tank() {
        super(tankVertices(),
                VehicleType.TANK,
                Config.TANK_HEALTH,      // High health
                Config.TANK_MAX_SPEED,   // Slow movement
                Config.TANK_TURN_SPEED,  // Slow turning
                List.of(new Seat(true, new MountedWeapon(
                                new Vector2D(0, 0), // Driver seat at center - main turret
                                WeaponFactory.getWeapon("Rocket"),
                                0.0, // Default angle (forward)
                                Math.PI / 2, // ±45° traverse range
                                2.2
                        )),
                        new Seat(false, new MountedWeapon(
                                new Vector2D(-Config.TANK_WIDTH / 3, Config.TANK_LENGTH / 4), // Left side gunner
                                WeaponFactory.getWeapon("Assault"),
                                Math.PI / 2, // Default angle (left side)
                                Math.PI / 3, // ±30° traverse range
                                1.5
                        )),
                        new Seat(false, new MountedWeapon(
                                new Vector2D(-Config.TANK_WIDTH / 3, -Config.TANK_LENGTH / 4), // Right side gunner
                                WeaponFactory.getWeapon("Assault"),
                                -Math.PI / 2, // Default angle (right side)
                                Math.PI / 3, // ±30° traverse range
                                1.5
                        ))
                ));

        setPosition(new Vector2D(0, 0));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Tank movement: forward/backward with turning
        double moveInput = -input.getMoveY(); // Forward/backward (inverted for tank controls)
        double turnInput = input.getMoveX(); // Left/right turning

        // Apply turning and rotate the vehicle geometry
        if (Math.abs(turnInput) > 0.01) {
            double angleChange = turnInput * turnSpeed * delta;
            angle += angleChange;
            // Rotate the vehicle's vertices to match the new angle
            rotate(angleChange);
        }

        // Apply movement in the direction the tank is facing
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
        return "Tank";
    }
}
