package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class Mech extends Vehicle {

    static List<Vector2D> mechVertices() {
        return List.of(
                new Vector2D(-Config.MECH_RADIUS, -Config.MECH_RADIUS),
                new Vector2D(Config.MECH_RADIUS, -Config.MECH_RADIUS),
                new Vector2D(Config.MECH_RADIUS, Config.MECH_RADIUS),
                new Vector2D(-Config.MECH_RADIUS, Config.MECH_RADIUS)
        );
    }

    public Mech(double x, double y) {
        super(mechVertices(), VehicleType.MECH,
                Config.MECH_HEALTH,      // Medium health
                Config.MECH_MAX_SPEED,   // Medium speed
                Config.MECH_TURN_SPEED,  // Fast turning
                0);                      // No passengers, driver only

        setPosition(new Vector2D(x, y));

        // Dual laser guns for the pilot
        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Laser Pistol"),
                Math.PI / 12
        ));

        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Laser Pistol"),
                -Math.PI / 12
        ));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        if (driverId == null) return;

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
        double dx = input.getMouseX() - x;
        double dy = input.getMouseY() - y;
        if (dx != 0 || dy != 0) {
            angle = Math.atan2(dy, dx);
        }
    }

    @Override
    public boolean enterVehicle(Player player) {
        boolean entered = super.enterVehicle(player);
        if (entered && driverId != null && driverId.equals(player.id())) {
            // Driver controls both weapons
            for (MountedWeapon weapon : mountedWeapons) {
                weapon.setControllerId(player.id());
            }
        }
        return entered;
    }

    @Override
    public String getVehicleName() {
        return "Mech";
    }
}
