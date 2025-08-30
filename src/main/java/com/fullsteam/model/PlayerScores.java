package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Represents player scores that are sent separately from the main game state
 * to reduce bandwidth usage. Scores don't need to be updated as frequently
 * as position/movement data.
 */
@Introspected
public record PlayerScores(
        String type,
        List<PlayerScore> scores,
        long timestamp
) {

    /**
     * Individual player score data
     */
    @Introspected
    public record PlayerScore(
            long id,
            String name,
            int team,
            int kills,
            int deaths) {
    }
}
