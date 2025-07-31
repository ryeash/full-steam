package com.fullsteam.model.ai;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines distinct behavioral personalities for AI players.
 * This influences how an AI prioritizes actions within a given strategy.
 */
public enum AIArchetype {
    /**
     * A default, well-rounded AI that balances offense, defense, and objectives.
     */
    BALANCED,

    /**
     * A highly aggressive AI that prioritizes seeking out and engaging enemies.
     */
    WARRIOR,

    /**
     * A defensive AI that prioritizes protecting teammates and objectives.
     */
    GUARDIAN,

    /**
     * A focused AI that aggressively pursues the primary game mode objective.
     */
    OBJECTIVE_HOUND;

    public static AIArchetype randomArchetype() {
        return values()[ThreadLocalRandom.current().nextInt(values().length)];
    }
}