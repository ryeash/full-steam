package com.fullsteam;

import com.fullsteam.model.Weapon;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A factory for creating and caching predefined weapon types. This provides a
 * central place to define standard weapon loadouts for the game.
 */
public class WeaponFactory {

    private static final Map<String, Weapon> weaponPresets = new ConcurrentHashMap<>();

    static {
        addPreset(new Weapon(
                "Assault Rifle",
                "AR",
                32, // Fire Rate: 300ms cooldown (fast)
                20, // Damage: 50 (good)
                6,  // Range: 500 (good)
                8,  // Speed: 9.0 (fast)
                12, // Accuracy: 0.25 spread (accurate)
                0,  // Multi-shot: 1 pellet
                8, // Magazine Size: 20 rounds
                14  // Reload Speed: 3.4s (average)
        ));

        addPreset(new Weapon(
                "Sniper Rifle",
                "SR",
                0,  // Fire Rate: 1000ms cooldown (very slow)
                40, // Damage: 90 (very high)
                18, // Range: 1000 (max)
                15, // Speed: 12.5 (very fast)
                21, // Accuracy: 0.13 spread (very accurate)
                0,  // Multi-shot: 1 pellet
                0,  // Magazine Size: 10 rounds (base)
                6   // Reload Speed: 4.1s (slow)
        ));

        addPreset(new Weapon(
                "SMG",
                "SMG",
                36, // Fire Rate: 125ms cooldown (very fast)
                11, // Damage: 34 (low-medium)
                4,  // Range: 300 (short)
                10, // Speed: 10.0 (fast)
                5,  // Accuracy: 0.325 spread (low)
                0,  // Multi-shot: 1 pellet
                20, // Magazine Size: 30 rounds (large)
                14  // Reload Speed: 3.6s (fast)
        ));

        addPreset(new Weapon(
                "Minigun",
                "M",
                42, // Fire Rate: 50ms cooldown (max)
                5,  // Damage: 20 (low)
                2,  // Range: 200 (very short)
                12,  // Speed: 7.5 (average)
                0,  // Accuracy: 0.4 spread (very low)
                0,  // Multi-shot: 1 pellet
                31, // Magazine Size: >100 rounds (huge)
                8  // Reload Speed: 4.0s (slow)
        ));

        addPreset(new Weapon(
                "Flamethrower",
                "F",
                38, // Fire Rate: 50ms cooldown (max)
                10, // Damage: 30 (low)
                1,  // Range: 150 (extremely short)
                1,  // Speed: 6.0 (slow)
                5,  // Accuracy: 0.325 spread (low)
                0,  // Multi-shot: 1 pellet
                31, // Magazine Size: 40 "ammo"
                14  // Reload Speed: 3.6s (fast)
        ));

        addPreset(new Weapon(
                "Shotgun",
                "S",
                4,  // Fire Rate: 900ms cooldown (slow)
                10, // Damage: 30 per pellet (high potential)
                4,  // Range: 250 (very short)
                10,  // Speed: 7.5 (average)
                2,  // Accuracy: 0.385 spread (very wide)
                50, // Multi-shot: 5 pellets
                10,  // Magazine Size: 15 rounds
                10  // Reload Speed: 3.8s (slow)
        ));

        addPreset(new Weapon(
                "Street Sweeper",
                "SW",
                37,  // Fire Rate: 875ms cooldown (slow)
                5,  // Damage: 20 per pellet (medium potential)
                2,  // Range: 200 (very short)
                0,  // Speed: 6.0 (slow)
                0,  // Accuracy: 0.4 spread (max spread)
                50, // Multi-shot: 6 pellets
                4,  // Magazine Size: 14 rounds
                2   // Reload Speed: 4.8s (very slow)
        ));

        addPreset(new Weapon(
                "Twin Sixes",
                "T6s",
                20, // Fire Rate: 500ms cooldown (medium)
                30, // Damage: 60 (high)
                4,  // Range: 350 (short-medium)
                21, // Speed: 12.5 (very fast)
                8, // Accuracy: 0.25 spread (accurate)
                10, // Multi-shot: 2 pellets
                2,  // Magazine Size: 12 rounds
                5   // Reload Speed: 4.5s (slow)
        ));
    }

    /**
     * These are the zombie specific weapons, they are not included in
     * the preset list because we don't want them to be selectable
     * or randomly assigned to non-zombie AIs.
     */
    public static final Weapon ZOMBIE_CLAW = new Weapon(
            "Claw",
            "C",
            10,
            30,
            -1,
            27,
            0,
            10,
            4,
            20
    );
    public static final Weapon HEAVY_ZOMBIE_CLAW = new Weapon(
            "HeavyClaw",
            "HC",
            10,
            50,
            -1,
            7,
            0,
            10,
            4,
            20
    );

    public static void addPreset(Weapon weapon) {
        if (weaponPresets.containsKey(weapon.getName())) {
            throw new IllegalArgumentException("weapon already registered: " + weapon.getName());
        }
        weaponPresets.put(weapon.getName(), weapon);
    }

    public static List<String> weaponOptions() {
        return weaponPresets.keySet().stream().sorted().toList();
    }

    /**
     * Retrieves a weapon preset by name.
     *
     * @param name The name of the weapon (e.g., "Sniper Rifle").
     * @return The requested Weapon, or the default "Assault Rifle" if not found.
     */
    public static Weapon getWeapon(String name) {
        return weaponPresets.getOrDefault(name, getDefaultWeapon());
    }

    /**
     * Gets the default weapon for new players.
     *
     * @return The default "Assault Rifle".
     */
    public static Weapon getDefaultWeapon() {
        return weaponPresets.get("Assault Rifle");
    }

    public static Weapon getRandomWeapon() {
        Object[] values = weaponPresets.values().toArray();
        return (Weapon) values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}