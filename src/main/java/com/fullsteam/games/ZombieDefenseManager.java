package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.ZombieAIStrategy;
import com.fullsteam.model.gamemodes.ZombieDefenseInfo;
import io.netty.channel.Channel;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.ZOMBIE_INITIAL_WAVE_DELAY_MS;
import static com.fullsteam.Config.ZOMBIE_TIME_BETWEEN_WAVES_MS;

/**
 * A cooperative PvE game mode where human players (Team 1) defend against
 * waves of AI-controlled zombies (Team 2).
 */
public class ZombieDefenseManager extends AbstractGameStateManager {

    private static final long WAVE_WARNING_TIME_MS = 5_000; // 5 seconds before the wave hits
    private int waveNumber = 0;
    private long nextWaveTime;
    private long roundEndTime;

    public ZombieDefenseManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Zombie Defense";
    }

    @Override
    public Player addPlayer(String playerId, Channel channel) {
        // All human players are on Team 1 (Survivors)
        Player player = new Player(playerId, 0, 0, 1);
        setValidSpawnPosition(player); // This will spawn them inside the house
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined the Survivors (Team 1)", playerId);
        return player;
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();
        roundEndTime = System.currentTimeMillis() + (Config.ROUND_DURATION_SECONDS * 1000);
        this.waveNumber = 0;
        // Schedule the first wave
        this.nextWaveTime = System.currentTimeMillis() + ZOMBIE_INITIAL_WAVE_DELAY_MS;
        log.info("Zombie Defense match started. Survive for {} seconds.", Config.ROUND_DURATION_SECONDS);
        sendGameEvent(GameEvent.yellow("First wave incoming..."));
    }

    @Override
    protected void updateGame() {
        super.updateGame();
        if (System.currentTimeMillis() >= nextWaveTime) {
            spawnNextWave();
        }
    }

    private void spawnNextWave() {
        waveNumber++;
        int zombiesToSpawn = 5 + (waveNumber * 3); // Waves get progressively harder
        log.info("Spawning Wave {} with {} zombies.", waveNumber, zombiesToSpawn);
        sendGameEvent(GameEvent.red("Wave " + waveNumber + " has arrived!"));

        for (int i = 0; i < zombiesToSpawn; i++) {
            spawnZombie();
        }

        // Schedule the next wave
        this.nextWaveTime = System.currentTimeMillis() + ZOMBIE_TIME_BETWEEN_WAVES_MS;
        // Schedule a warning message to appear before the next wave
        long warningDelay = ZOMBIE_TIME_BETWEEN_WAVES_MS - WAVE_WARNING_TIME_MS;
        if (warningDelay > 0) {
            final int nextWaveNumber = this.waveNumber + 1;
            gameLoop.schedule(() -> {
                // Check if the game is still running to avoid sending messages after game over
                if (System.currentTimeMillis() < roundEndTime && !isRoundOver) {
                    sendGameEvent(GameEvent.yellow("Wave " + nextWaveNumber + " is incoming!"));
                }
            }, warningDelay, TimeUnit.MILLISECONDS);
        }
    }

    private void spawnZombie() {
        String playerId = "zombie-" + UUID.randomUUID();
        // Zombies are on Team 2
        AIPlayer zombie = new AIPlayer(playerId, 0, 0, 2, new ZombieAIStrategy(), AIArchetype.randomArchetype());
        double random = ThreadLocalRandom.current().nextDouble();

        // Introduce special zombies in later waves
        if (waveNumber > 5 && random < 0.15) { // 15% chance for a Brute
            zombie.setPlayerName("Brute");
            zombie.setWeapon(WeaponFactory.HEAVY_ZOMBIE_CLAW);
            zombie.setMaxHealth(300);
            zombie.setSpeed(Config.ZOMBIE_SPEED - .6);
        } else if (waveNumber > 3 && random < 0.30) { // 30% chance for a Runner
            zombie.setPlayerName("Runner");
            zombie.setWeapon(WeaponFactory.ZOMBIE_CLAW);
            zombie.setMaxHealth(50);
            zombie.setSpeed(Config.ZOMBIE_SPEED + .6);
        } else {
            zombie.setPlayerName("Zombie");
            zombie.setWeapon(WeaponFactory.ZOMBIE_CLAW);
            zombie.setMaxHealth(50);
            zombie.setSpeed(Config.ZOMBIE_SPEED);
        }
        zombie.resetHealth();
        // Spawn zombies at the edges of the map
        setZombieSpawnPosition(zombie);
        players.put(playerId, zombie);
    }

    private void setZombieSpawnPosition(Player zombie) {
        // Logic to spawn zombies around the map edges, outside the house
        double x, y;
        int edge = ThreadLocalRandom.current().nextInt(4);
        switch (edge) {
            case 0: // Top edge
                x = ThreadLocalRandom.current().nextDouble(Config.GAME_WIDTH);
                y = 10;
                break;
            case 1: // Bottom edge
                x = ThreadLocalRandom.current().nextDouble(Config.GAME_WIDTH);
                y = Config.GAME_HEIGHT - Config.PLAYER_SIZE - 10;
                break;
            case 2: // Left edge
                x = 10;
                y = ThreadLocalRandom.current().nextDouble(Config.GAME_HEIGHT);
                break;
            default: // Right edge
                x = Config.GAME_WIDTH - Config.PLAYER_SIZE - 10;
                y = ThreadLocalRandom.current().nextDouble(Config.GAME_HEIGHT);
                break;
        }
        zombie.setX(x);
        zombie.setY(y);
    }

    @Override
    protected void checkAndRespawnPlayers() {
        // In this mode, human players do not respawn.
        // Zombies are removed on death and new ones are added in waves.
    }

    @Override
    protected boolean checkEndConditions() {
        long humansAlive = players.values().stream()
                .filter(p -> p.getTeam() == 1 && !p.isDead())
                .count();
        // Use an if / else if structure to prevent incorrect win conditions
        if (humansAlive == 0 && hasHumanPlayers()) {
            log.info("All survivors have been eliminated. Zombies win!");
            sendGameEvent(GameEvent.info("The horde has won! Game Over."));
            return true; // Return immediately after a condition is met
        } else if (System.currentTimeMillis() >= roundEndTime) {
            log.info("Survivors have held out until the end. Survivors win!");
            sendGameEvent(GameEvent.info("You have survived! Victory!"));
            return true;
        }
        return false;
    }

    @Override
    protected void generateObstacles() {
        obstacles.clear();
        // Create a "house" in the middle of the map.
        double houseWidth = 300;
        double houseHeight = 200;
        double wallThickness = 20;
        double doorSize = 60;

        double centerX = Config.GAME_WIDTH / 2.0;
        double centerY = Config.GAME_HEIGHT / 2.0;
        double left = centerX - houseWidth / 2;
        double right = centerX + houseWidth / 2;
        double top = centerY - houseHeight / 2;
        double bottom = centerY + houseHeight / 2;

        // Top wall (with a door gap)
        obstacles.add(Obstacle.createRectangle(left, top, (houseWidth - doorSize) / 2, wallThickness));
        obstacles.add(Obstacle.createRectangle(centerX + doorSize / 2, top, (houseWidth - doorSize) / 2, wallThickness));

        // Bottom wall (with a door gap)
        obstacles.add(Obstacle.createRectangle(left, bottom - wallThickness, (houseWidth - doorSize) / 2, wallThickness));
        obstacles.add(Obstacle.createRectangle(centerX + doorSize / 2, bottom - wallThickness, (houseWidth - doorSize) / 2, wallThickness));

        // Left wall
        obstacles.add(Obstacle.createRectangle(left, top, wallThickness, houseHeight));
        // Right wall
        obstacles.add(Obstacle.createRectangle(right - wallThickness, top, wallThickness, houseHeight));

        log.info("Generated a house structure for Zombie Defense.");
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        // Human players should spawn inside the house
        if (player.getTeam() == 1) {
            double houseWidth = 300;
            double houseHeight = 200;
            double centerX = Config.GAME_WIDTH / 2.0;
            double centerY = Config.GAME_HEIGHT / 2.0;
            player.setX(centerX + (ThreadLocalRandom.current().nextDouble() - 0.5) * (houseWidth - 100));
            player.setY(centerY + (ThreadLocalRandom.current().nextDouble() - 0.5) * (houseHeight - 100));
        }
        // This case is for zombies, who have their own spawn logic.
        // This method will be called from super.startNewRound(), but we can ignore it
        // as setZombieSpawnPosition() will be used for actual zombie placement.
    }

    @Override
    protected GameState buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        long timeToNextWave = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(nextWaveTime - System.currentTimeMillis()));
        long zombiesAlive = players.values().stream().filter(p -> p.getTeam() == 2 && !p.isDead()).count();
        return new GameState(
                players.values(),
                bullets,
                obstacles,
                hazards,
                deathMarkers,
                gameEvents,
                new ZombieDefenseInfo(
                        this.waveNumber,
                        zombiesAlive,
                        timeToNextWave,
                        roundTimeRemainingSeconds
                )
        );
    }
}