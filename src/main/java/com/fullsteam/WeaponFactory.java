package com.fullsteam;

import com.fullsteam.model.Explosion;
import com.fullsteam.model.PoisonCloud;
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
    private static final List<String> weaponNames;
    private static final Weapon[] weaponArray;

    static {
        addPreset(new Weapon(
                "Assault",
                "A",
                30, // Fire Rate
                20, // Damage
                6,  // Range
                8,  // Speed
                8,  // Speed Decay
                10, // Accuracy
                0,  // Multi-shot
                8, // Magazine Size
                10,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Sniper Rifle",
                "SR",
                0,  // Fire Rate
                35, // Damage
                18, // Range
                15, // Speed
                10, // Speed Decay (no decay)
                16, // Accuracy
                0,  // Multi-shot
                0,  // Magazine Size
                6,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "SMG",
                "SMG",
                34, // Fire Rate
                11, // Damage
                4,  // Range
                10, // Speed
                5,  // Speed Decay
                5,  // Accuracy
                0,  // Multi-shot
                17, // Magazine Size
                14,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Minigun",
                "M",
                40, // Fire Rate
                6,  // Damage
                1,  // Range
                12, // Speed
                2,  // Speed Decay
                0,  // Accuracy
                0,  // Multi-shot
                31, // Magazine Size
                8,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Flamethrower",
                "F",
                38, // Fire Rate
                10, // Damage
                1,  // Range
                1,  // Speed
                0,  // Speed Decay (max decay)
                5,  // Accuracy
                0,  // Multi-shot
                31, // Magazine Size
                14,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Shotgun",
                "S",
                4,  // Fire Rate
                10, // Damage
                4,  // Range
                10, // Speed
                2,  // Speed Decay
                2,  // Accuracy
                50, // Multi-shot
                8,  // Magazine Size
                10,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Street Sweeper",
                "SW",
                27,  // Fire Rate
                5,  // Damage
                2,  // Range
                0,  // Speed
                0,  // Speed Decay
                -20, // Accuracy
                70, // Multi-shot
                14,  // Magazine Size
                2,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Twin Sixes",
                "T6s",
                19, // Fire Rate
                15, // Damage
                4,  // Range
                18, // Speed
                9,  // Speed Decay
                8,  // Accuracy
                10, // Multi-shot
                2,  // Magazine Size
                15,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Rocket",
                "R",
                5, // Fire Rate
                -5, // Damage
                18,  // Range
                18, // Speed
                10,  // Speed Decay
                1,  // Accuracy
                0, // Multi-shot
                -2,  // Magazine Size
                -5,  // Reload Speed
                Explosion::rocket
        ));

        addPreset(new Weapon(
                "Grenade Launcher",
                "GR",
                1, // Fire Rate
                0, // Damage
                20,  // Range
                9, // Speed
                0,  // Speed Decay
                0,  // Accuracy
                0, // Multi-shot
                0,  // Magazine Size
                10,  // Reload Speed
                Explosion::grenade
        ));

        addPreset(new Weapon(
                "Poison Launcher",
                "P",
                2, // Fire Rate
                0, // Damage (damage is from the cloud)
                15,  // Range
                8, // Speed
                2,  // Speed Decay
                5,  // Accuracy
                0, // Multi-shot
                -1,  // Magazine Size
                9,  // Reload Speed
                PoisonCloud::create
        ));

        addPreset(new Weapon(
                "DMR",
                "DMR",
                10, // Fire Rate
                30, // Damage
                15, // Range
                12, // Speed
                6,  // Speed Decay
                14, // Accuracy
                0,  // Multi-shot
                5,  // Magazine Size
                8,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Tactical Rifle",
                "TR",
                8,  // Fire Rate
                12, // Damage
                8,  // Range
                10, // Speed
                8,  // Speed Decay
                12, // Accuracy
                20, // Multi-shot (3-round burst)
                10, // Magazine Size
                12, // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Hand Cannon",
                "REV", // Revolver
                3,  // Fire Rate
                55, // Damage
                4,  // Range
                15, // Speed
                8,  // Speed Decay
                11, // Accuracy
                0,  // Multi-shot
                0,  // Magazine Size (6 shots)
                4,  // Reload Speed
                null
        ));

        // Pre-sort the weapon names for faster access.
        weaponNames = weaponPresets.keySet().stream().sorted().toList();

        // Cache the weapon array for faster random access.
        weaponArray = weaponPresets.values().toArray(new Weapon[0]);
    }

    /**
     * These are the zombie specific weapons, they are not included in
     * the preset list because we don't want them to be selectable
     * or randomly assigned to non-zombie AIs.
     */
    public static final Weapon ZOMBIE_CLAW = new Weapon(
            "Claw",
            "C",
            5,
            20,
            -1,
            27,
            0,
            0,
            10,
            19,
            20,
            null
    );
    public static final Weapon HEAVY_ZOMBIE_CLAW = new Weapon(
            "HeavyClaw",
            "HC",
            10,
            50,
            -1,
            7,
            0,
            0,
            10,
            4,
            20,
            null
    );

    public static void addPreset(Weapon weapon) {
        if (weaponPresets.containsKey(weapon.getName())) {
            throw new IllegalArgumentException("weapon already registered: " + weapon.getName());
        }
        weaponPresets.put(weapon.getName(), weapon);
    }

    public static List<String> weaponOptions() {
        return weaponNames;
    }

    /**
     * Retrieves a weapon preset by name.
     *
     * @param name The name of the weapon (e.g., "Sniper Rifle").
     * @return The requested Weapon, or the default "Assault" if not found.
     */
    public static Weapon getWeapon(String name) {
        return weaponPresets.getOrDefault(name, getDefaultWeapon());
    }

    /**
     * Gets the default weapon for new players.
     *
     * @return The default "Assault".
     */
    public static Weapon getDefaultWeapon() {
        return weaponArray[0];
    }

    public static Weapon getRandomWeapon() {
        return weaponArray[ThreadLocalRandom.current().nextInt(weaponArray.length)];
    }
}