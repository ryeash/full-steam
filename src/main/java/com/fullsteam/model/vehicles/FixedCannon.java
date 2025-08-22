package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class FixedCannon extends Vehicle {

    private static List<Vector2D> cannonVertices() {
        // Create a centered octagon around origin (0,0)
        double r = Config.FIXED_CANNON_RADIUS;
        List<Vector2D> vertices = new java.util.ArrayList<>();

        // Generate 8 vertices for an octagon
        for (int i = 0; i < 8; i++) {
            double angle = (i * 2 * Math.PI) / 8;
            double x = r * Math.cos(angle);
            double y = r * Math.sin(angle);
            vertices.add(new Vector2D(x, y));
        }

        return vertices;
    }

    public FixedCannon() {
        super(cannonVertices(), VehicleType.FIXED_CANNON,
                Config.FIXED_CANNON_HEALTH,   // Medium health
                0.0,                          // No movement (immobile)
                Config.FIXED_CANNON_TURN_SPEED, // Can rotate to aim
                List.of(new Seat(true, new MountedWeapon(
                        new Vector2D(0, 0), // align with vehicle center
                        WeaponFactory.FIXED_CANNON_WEAPON,
                        0.0, // Default angle (forward)
                        Math.PI / 6,// ±15° traverse range (very limited like real artillery),
                        3.0
                ))));
        setPosition(new Vector2D(0, 0));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Fixed cannon can rotate its base with left/right input
        double turnInput = input.getMoveX(); // Left/right turning

        // Apply turning and rotate the vehicle geometry (limited by turn speed)
        if (Math.abs(turnInput) > 0.01) {
            double angleChange = turnInput * turnSpeed * delta;
            angle += angleChange;
            // Rotate the vehicle's vertices to match the new angle
            rotate(angleChange);
        }

        // Always stationary (no movement)
        velocityX = 0;
        velocityY = 0;
        speed = 0;
    }

    @Override
    public String getVehicleName() {
        return "Fixed Cannon";
    }
}
