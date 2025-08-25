package com.fullsteam.model.gamemodes;

/**
 * Contains the game-state information specific to the Zombie Defense game mode.
 */
public final class ZombieDefenseInfo extends GameInfo {
    private final int waveNumber;
    private final long zombiesAlive;
    private final long timeUntilNextWave;
    private final long timeLeft;

    /**
     *
     */
    public ZombieDefenseInfo(int waveNumber,
                             long zombiesAlive,
                             long timeUntilNextWave,
                             long timeLeft) {
        this.waveNumber = waveNumber;
        this.zombiesAlive = zombiesAlive;
        this.timeUntilNextWave = timeUntilNextWave;
        this.timeLeft = timeLeft;
    }

    @Override
    public String getType() {
        return "Zombie Defense";
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    public long getZombiesAlive() {
        return zombiesAlive;
    }

    public long getTimeUntilNextWave() {
        return timeUntilNextWave;
    }

    public long getTimeLeft() {
        return timeLeft;
    }
}