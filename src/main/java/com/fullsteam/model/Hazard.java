package com.fullsteam.model;

/**
 * Represents an environmental hazard on the map.
 *
 * @param type        The type of hazard (e.g., SLOW).
 * @param position    The center point of the hazard.
 * @param radius      The radius of the hazard's area of effect.
 * @param radiusSq    The squared radius for efficient distance checking.
 * @param effectValue The modifier for the hazard's effect (e.g., a slow factor of 0.5 for 50% speed).
 */
public record Hazard(Type type,
                     Vector2D position,
                     double radius,
                     double radiusSq,
                     double effectValue) {
    /**
     * Defines the types of environmental hazards that can exist in the game.
     */
    public enum Type {
        /**
         * A zone that slows player movement speed.
         */
        SLOW,
        /**
         * A damaging zone, i.e. fire
         */
        DAMAGE,
        //    /**
        //     * A slippery zone, players slide across it, unable to change direction
        //     */
        //    SLIPPERY
    }
}