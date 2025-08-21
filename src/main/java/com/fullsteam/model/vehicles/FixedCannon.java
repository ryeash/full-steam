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
                        WeaponFactory.getWeapon("Rocket"),
                        0.0
                ))));
        setPosition(new Vector2D(0, 0));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // Fixed cannon doesn't move, but can rotate to aim
        // Rotation is handled by aiming towards mouse
        double dx = input.getMouseX() - position().x();
        double dy = input.getMouseY() - position().y();
        if (dx != 0 || dy != 0) {
            angle = Math.atan2(dy, dx);
        }

        // Always stationary
        velocityX = 0;
        velocityY = 0;
        speed = 0;
    }

    @Override
    public String getVehicleName() {
        return "Fixed Cannon";
    }
}
