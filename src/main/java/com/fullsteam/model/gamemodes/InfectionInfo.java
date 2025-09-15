package com.fullsteam.model.gamemodes;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Contains the game-state information specific to the Infection game mode.
 * Survivors must outlast the infected until time runs out.
 * Infected must spread the virus to all survivors.
 */
@Introspected
public final class InfectionInfo extends GameInfo {
    private final int survivorCount;
    private final int infectedCount;
    private final long timeLeft;
    private final List<String> survivorNames;
    private final List<String> infectedNames;
    private final boolean gameStarted;
    private final long infectionStartTime;

    /**
     * @param survivorCount     Number of survivors remaining
     * @param infectedCount     Number of infected players
     * @param timeLeft          Time remaining in the round (seconds)
     * @param survivorNames     Names of surviving players
     * @param infectedNames     Names of infected players
     * @param gameStarted       Whether the infection phase has begun
     * @param infectionStartTime Time when infection phase starts (for countdown)
     */
    public InfectionInfo(int survivorCount, int infectedCount, long timeLeft,
                         List<String> survivorNames, List<String> infectedNames,
                         boolean gameStarted, long infectionStartTime) {
        this.survivorCount = survivorCount;
        this.infectedCount = infectedCount;
        this.timeLeft = timeLeft;
        this.survivorNames = survivorNames;
        this.infectedNames = infectedNames;
        this.gameStarted = gameStarted;
        this.infectionStartTime = infectionStartTime;
    }

    @Override
    public String getType() {
        return "Infection";
    }

    public int getSurvivorCount() {
        return survivorCount;
    }

    public int getInfectedCount() {
        return infectedCount;
    }

    public long getTimeLeft() {
        return timeLeft;
    }

    public List<String> getSurvivorNames() {
        return survivorNames;
    }

    public List<String> getInfectedNames() {
        return infectedNames;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public long getInfectionStartTime() {
        return infectionStartTime;
    }
}
