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
                                new Vector2D(0, Config.MECH_HEIGHT / 2), // Driver seat at front center
                                WeaponFactory.getWeapon("Laser Pistol"),
                                0.0)),
                        new Seat(false, new MountedWeapon(
                                new Vector2D(0, -Config.MECH_HEIGHT / 2), // Gunner seat at rear center
                                WeaponFactory.getWeapon("Laser Pistol"),
                                0.0)) // Butt lasers!
                ));

        setPosition(new Vector2D(0, 0));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Mech movement: strafing in any direction like a player
        double moveX = input.getMoveX();
        double moveY = input.getMoveY();

        Vector2D moveVector = new Vector2D(moveX, moveY);
        double magnitude = moveVector.magnitude();

        // Sanitize input: clamp magnitude to 1.0
        if (magnitude > 1.0) {
            moveVector = moveVector.normalize();
            magnitude = 1.0;
        }

        if (magnitude > 0.01) {
            double currentSpeed = maxSpeed * magnitude;
            Vector2D directionVector = moveVector.normalize();
            Vector2D velocity = directionVector.multiply(currentSpeed);

            velocityX = velocity.x();
            velocityY = velocity.y();
            speed = currentSpeed;
        } else {
            velocityX = 0;
            velocityY = 0;
            speed = 0;
        }

        // Mech faces towards mouse cursor
        double dx = input.getMouseX() - position().x();
        double dy = input.getMouseY() - position().y();
        if (dx != 0 || dy != 0) {
            double newAngle = Math.atan2(dy, dx);
            double angleChange = newAngle - angle;

            // Normalize angle change to be between -π and π
            while (angleChange > Math.PI) angleChange -= 2 * Math.PI;
            while (angleChange < -Math.PI) angleChange += 2 * Math.PI;

            if (Math.abs(angleChange) > 0.01) {
                angle = newAngle;
                // Rotate the vehicle's vertices to match the new angle
                rotate(angleChange);
            }
        }
    }

    @Override
    public String getVehicleName() {
        return "Mech";
    }
}
