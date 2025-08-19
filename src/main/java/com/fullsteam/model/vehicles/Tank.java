package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

public class Tank extends Vehicle {

    static List<Vector2D> tankVertices() {
        return List.of(
                new Vector2D(-Config.TANK_WIDTH / 2, -Config.TANK_LENGTH / 2),
                new Vector2D(Config.TANK_WIDTH / 2, -Config.TANK_LENGTH / 2),
                new Vector2D(Config.TANK_WIDTH / 2, Config.TANK_LENGTH / 2),
                new Vector2D(-Config.TANK_WIDTH / 2, Config.TANK_LENGTH / 2)
        );
    }

    public Tank(double x, double y) {
        super(tankVertices(),
                VehicleType.TANK,
                Config.TANK_HEALTH,      // High health
                Config.TANK_MAX_SPEED,   // Slow movement
                Config.TANK_TURN_SPEED,  // Slow turning
                2);                      // 2 passengers + driver = 3 total

        setPosition(new Vector2D(x, y));

        // Add main cannon for driver (controlled by driver input)
        // Add two fast-firing weapons for passengers
        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Rocket"), // TODO
                0.0
        ));

        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Assault"), // TODO
                Math.PI / 4
        ));

        mountedWeapons.add(new MountedWeapon(
                WeaponFactory.getWeapon("Assault"), // TODO
                -Math.PI / 4
        ));
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        if (driverId == null) {
            return;
        }

        // Tank movement: forward/backward with turning
        double moveInput = input.getMoveY(); // Forward/backward
        double turnInput = input.getMoveX(); // Left/right turning

        // Apply turning
        if (Math.abs(turnInput) > 0.01) {
            angle += turnInput * turnSpeed * delta;
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
    public boolean enterVehicle(Player player) {
        boolean entered = super.enterVehicle(player);
        if (entered) {
            // Assign weapons based on position
            if (driverId != null && driverId.equals(player.id())) {
                // Driver gets main cannon
                if (!mountedWeapons.isEmpty()) {
                    mountedWeapons.getFirst().setControllerId(player.id());
                }
            } else {
                // Passengers get machine guns
                for (int i = 1; i < mountedWeapons.size(); i++) {
                    if (mountedWeapons.get(i).getControllerId() == null) {
                        mountedWeapons.get(i).setControllerId(player.id());
                        break;
                    }
                }
            }
        }
        return entered;
    }

    @Override
    public String getVehicleName() {
        return "Tank";
    }
}
