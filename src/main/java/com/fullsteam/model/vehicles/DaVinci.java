package com.fullsteam.model.vehicles;

import com.fullsteam.Config;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.MountedWeapon;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;

import java.util.List;

/**
 * DaVinci - A pentagon-shaped vehicle with 5 seats, each equipped with different weapons
 * positioned at the pentagon's vertices. The front is oriented to a vertex (pointing forward).
 */
public class DaVinci extends Vehicle {

    static List<Vector2D> daVinciVertices() {
        // Create a regular pentagon with front vertex pointing forward (positive X direction)
        // Pentagon vertices are calculated with the front vertex at the top (0° rotation)
        double radius = Config.DAVINCI_RADIUS;
        
        // Pentagon vertices starting from front vertex (pointing right/positive X when angle = 0)
        // and going clockwise around the pentagon
        return List.of(
                // Vertex 1: Front point (0°) - pointing forward/right when angle = 0
                new Vector2D(radius, 0),
                
                // Vertex 2: Front-right (72°)
                new Vector2D(radius * Math.cos(Math.toRadians(72)), radius * Math.sin(Math.toRadians(72))),
                
                // Vertex 3: Rear-right (144°)
                new Vector2D(radius * Math.cos(Math.toRadians(144)), radius * Math.sin(Math.toRadians(144))),
                
                // Vertex 4: Rear-left (216°)
                new Vector2D(radius * Math.cos(Math.toRadians(216)), radius * Math.sin(Math.toRadians(216))),
                
                // Vertex 5: Front-left (288°)
                new Vector2D(radius * Math.cos(Math.toRadians(288)), radius * Math.sin(Math.toRadians(288)))
        );
    }

    public DaVinci() {
        super(daVinciVertices(),
                VehicleType.DAVINCI,
                Config.DAVINCI_HEALTH,      // Medium-high health
                Config.DAVINCI_MAX_SPEED,   // Medium speed
                Config.DAVINCI_TURN_SPEED,  // Medium turning
                createSeats());

        setPosition(new Vector2D(0, 0));
    }

    private static List<Seat> createSeats() {
        double radius = Config.DAVINCI_RADIUS * 0.8; // Weapon mounts slightly inside the vertices
        
        return List.of(
                // Seat 1: Driver at front vertex (0°) - Rocket Launcher (heavy forward firepower)
                new Seat(true, new MountedWeapon(
                        new Vector2D(radius, 0), // Front vertex position
                        WeaponFactory.getWeapon("Rocket"),
                        0.0, // Default angle (forward)
                        Math.PI / 3, // ±60° traverse range
                        2.5 // High damage multiplier
                )),
                
                // Seat 2: Front-right vertex (72°) - Sniper Rifle (precision long-range)
                new Seat(false, new MountedWeapon(
                        new Vector2D(radius * Math.cos(Math.toRadians(72)), radius * Math.sin(Math.toRadians(72))),
                        WeaponFactory.getWeapon("Sniper Rifle"),
                        Math.toRadians(72), // Default angle (front-right)
                        Math.PI / 2, // ±90° traverse range
                        2.0 // Good damage multiplier
                )),
                
                // Seat 3: Rear-right vertex (144°) - Minigun (sustained fire)
                new Seat(false, new MountedWeapon(
                        new Vector2D(radius * Math.cos(Math.toRadians(144)), radius * Math.sin(Math.toRadians(144))),
                        WeaponFactory.getWeapon("Minigun"),
                        Math.toRadians(144), // Default angle (rear-right)
                        Math.PI / 2, // ±90° traverse range
                        1.8 // Moderate damage multiplier
                )),
                
                // Seat 4: Rear-left vertex (216°) - Flamethrower (close-range area denial)
                new Seat(false, new MountedWeapon(
                        new Vector2D(radius * Math.cos(Math.toRadians(216)), radius * Math.sin(Math.toRadians(216))),
                        WeaponFactory.getWeapon("Flamethrower"),
                        Math.toRadians(216), // Default angle (rear-left)
                        Math.PI / 2, // ±90° traverse range
                        2.2 // High damage multiplier for close range
                )),
                
                // Seat 5: Front-left vertex (288°) - Laser Minigun (energy weapon)
                new Seat(false, new MountedWeapon(
                        new Vector2D(radius * Math.cos(Math.toRadians(288)), radius * Math.sin(Math.toRadians(288))),
                        WeaponFactory.getWeapon("Laser Minigun"),
                        Math.toRadians(288), // Default angle (front-left)
                        Math.PI / 2, // ±90° traverse range
                        1.9 // Good damage multiplier
                ))
        );
    }

    @Override
    public void handleDriverInput(PlayerInput input, long delta) {
        // DaVinci movement: similar to tank but with better maneuverability
        double moveInput = -input.getMoveY(); // Forward/backward (inverted for vehicle controls)
        double turnInput = input.getMoveX(); // Left/right turning

        // Apply turning and rotate the vehicle geometry
        if (Math.abs(turnInput) > 0.01) {
            double angleChange = turnInput * turnSpeed * delta;
            angle += angleChange;
            // Rotate the vehicle's vertices to match the new angle
            rotate(angleChange);
        }

        // Apply movement in the direction the DaVinci is facing
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
        return "DaVinci";
    }
}
