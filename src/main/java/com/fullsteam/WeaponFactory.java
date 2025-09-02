package com.fullsteam;

import com.fullsteam.model.Explosion;
import com.fullsteam.model.GravityWell;
import com.fullsteam.model.GridPoint;
import com.fullsteam.model.Mine;
import com.fullsteam.model.PoisonCloud;
import com.fullsteam.model.SmokeCloud;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Weapon;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A factory for creating and caching predefined weapon types. This provides a
 * central place to define standard weapon loadouts for the game.
 */
public class WeaponFactory {

    private static final Map<String, Weapon> weaponPresets = new HashMap<>();
    private static final List<String> weaponNames;
    private static final Weapon[] weaponArray;
    private static final Weapon[] randomWeapons;

    static {
        addPreset(new Weapon(
                "Assault",
                "A",
                30, // Fire Rate
                22, // Damage
                10,  // Range
                10,  // Speed
                8,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                10, // Magazine Size
                10,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Sniper Rifle",
                "SR",
                0,  // Fire Rate
                46, // Damage
                28, // Range
                13, // Speed
                5, // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                3,  // Magazine Size
                5,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "SMG",
                "SMG",
                36, // Fire Rate
                13, // Damage
                4,  // Range
                11, // Speed
                5,  // Speed Decay
                0,  // Accuracy
                0,  // Multi-shot
                17, // Magazine Size
                14,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Minigun",
                "M",
                40, // Fire Rate
                8,  // Damage
                3,  // Range
                10, // Speed
                6,  // Speed Decay
                -10,  // Accuracy
                0,  // Multi-shot
                33, // Magazine Size
                10,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Flamethrower",
                "F",
                37, // Fire Rate
                14, // Damage
                2,  // Range
                5,  // Speed
                2,  // Speed Decay
                -6,  // Accuracy
                0,  // Multi-shot
                33, // Magazine Size
                13,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Shotgun",
                "S",
                4,  // Fire Rate
                14, // Damage
                8,  // Range
                9, // Speed
                2,  // Speed Decay
                -9,  // Accuracy
                50, // Multi-shot
                12,  // Magazine Size
                10,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Street Sweeper",
                "SW",
                24,  // Fire Rate
                7,  // Damage
                2,  // Range
                3,  // Speed
                0,  // Speed Decay
                -47, // Accuracy
                80, // Multi-shot
                27,  // Magazine Size
                4,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Twin Sixes",
                "T6s",
                22, // Fire Rate
                19, // Damage
                7,  // Range
                12, // Speed
                15,  // Speed Decay
                -4,  // Accuracy
                10, // Multi-shot
                4,  // Magazine Size
                15,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Rocket",
                "R",
                0, // Fire Rate
                0, // Damage
                17,  // Range
                16, // Speed
                9,  // Speed Decay
                0,  // Accuracy
                0, // Multi-shot
                0,  // Magazine Size
                -2,  // Reload Speed
                Explosion::rocket
        ));

        addPreset(new Weapon(
                "Grenade (Fragmentation)",
                "GR",
                1, // Fire Rate
                0, // Damage
                19,  // Range
                9, // Speed
                1,  // Speed Decay
                0,  // Accuracy
                0, // Multi-shot
                4,  // Magazine Size
                6,  // Reload Speed
                Explosion::grenade
        ));

        addPreset(new Weapon(
                "Grenade (Poison)",
                "GP",
                4, // Fire Rate
                0, // Damage (damage is from the cloud)
                14,// Range
                8, // Speed
                2, // Speed Decay
                0, // Accuracy
                0, // Multi-shot
                3, // Magazine Size
                9, // Reload Speed
                PoisonCloud::create
        ));

        addPreset(new Weapon(
                "Grenade (Smoke)",
                "SM",
                8, // Fire Rate
                0, // Damage (damage is from the cloud)
                8,// Range
                8, // Speed
                4, // Speed Decay
                0, // Accuracy
                0, // Multi-shot
                3, // Magazine Size
                9, // Reload Speed
                SmokeCloud::create
        ));

        addPreset(new Weapon(
                "Grenade (Gravity Well)",
                "GW",
                7, // Fire Rate
                0, // Damage (effect is from the gravity well)
                10,// Range
                8, // Speed
                3, // Speed Decay
                0, // Accuracy
                0, // Multi-shot
                2, // Magazine Size
                10, // Reload Speed
                GravityWell::create
        ));

        addPreset(new Weapon(
                "Marksman Rifle",
                "MR",
                10, // Fire Rate
                30, // Damage
                20, // Range
                15, // Speed
                9,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                6,  // Magazine Size
                10,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Tactical Rifle",
                "TR",
                11,  // Fire Rate
                17, // Damage
                12,  // Range
                12, // Speed
                10,  // Speed Decay
                -2, // Accuracy
                20, // Multi-shot (3-round burst)
                8, // Magazine Size
                12, // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Hand Cannon",
                "REV", // Revolver
                3,  // Fire Rate
                55, // Damage
                6,  // Range
                13, // Speed
                12,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                3,  // Magazine Size (6 shots)
                8,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Laser Pistol",
                "LAZ",
                Weapon.Ordinance.LASER,
                15,  // Fire Rate
                21, // Damage
                5,  // Range
                0, // Speed
                0,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                4,  // Magazine Size
                5,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Laser Rifle",
                "LR",
                Weapon.Ordinance.LASER,
                9,  // Fire Rate
                17, // Damage
                18,  // Range
                0, // Speed
                0,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                2,  // Magazine Size
                4,  // Reload Speed
                null
        ));

        addPreset(new Weapon(
                "Engineer Wrench",
                "EW",
                25,  // Fire Rate
                0, // Damage
                -5,  // Range
                15, // Speed
                0,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                1,  // Magazine Size (3 shots)
                4,  // Reload Speed
                Turret::create
        ));

        addPreset(new Weapon(
                "Mine Layer",
                "MI",
                25,  // Fire Rate
                0, // Damage
                -5,  // Range
                15, // Speed
                0,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                1,  // Magazine Size (3 shots)
                4,  // Reload Speed
                Mine::create
        ));

        addPreset(new Weapon(
                "Defense Grid",
                "DG",
                25,  // Fire Rate
                0, // Damage
                -5,  // Range
                14, // Speed
                0,  // Speed Decay
                0, // Accuracy
                0,  // Multi-shot
                2,  // Magazine Size (3 shots)
                4,  // Reload Speed
                GridPoint::create
        ));

        // Pre-sort the weapon names for faster access.
        weaponNames = weaponPresets.keySet().stream().sorted().toList();

        // Cache the weapon array for faster random access.
        weaponArray = weaponPresets.values()
                .stream()
                .sorted(Comparator.comparing(Weapon::getName))
                .toArray(Weapon[]::new);

        List<String> randomExceptions = List.of(
                "Engineer Wrench",
                "Grenade (Slow)",
                "Grenade (Smoke)",
                "Grenade (Gravity Well)",
                "Mine Layer",
                "Defense Grid");

        randomWeapons = weaponPresets.values()
                .stream()
                .filter(w -> !randomExceptions.contains(w.getName()))
                .sorted(Comparator.comparing(Weapon::getName))
                .toArray(Weapon[]::new);
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
            6,
            -2,
            15,
            0,
            0,
            10,
            19,
            47,
            null
    );
    public static final Weapon HEAVY_ZOMBIE_CLAW = new Weapon(
            "HeavyClaw",
            "HC",
            10,
            51,
            -2,
            7,
            0,
            0,
            10,
            4,
            20,
            null
    );

    public static final Weapon FIXED_CANNON_WEAPON = new Weapon(
            "EightyEight",
            "88",
            0, // Fire Rate
            0, // Damage
            16,  // Range
            15, // Speed
            9,  // Speed Decay
            0,  // Accuracy
            0, // Multi-shot
            0,  // Magazine Size
            0,  // Reload Speed
            Explosion::shell
    );

    public static final Weapon MECH_LAZ_CANNON = new Weapon(
            "Laser Minigun",
            "LM",
            Weapon.Ordinance.LASER,
            35,  // Fire Rate
            5, // Damage
            9,  // Range
            0, // Speed
            0,  // Speed Decay
            -10, // Accuracy
            0,  // Multi-shot
            10,  // Magazine Size
            1,  // Reload Speed
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
        return randomWeapons[ThreadLocalRandom.current().nextInt(randomWeapons.length)];
    }
}