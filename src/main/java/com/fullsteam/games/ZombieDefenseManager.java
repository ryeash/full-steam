package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.ZombiePlayer;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.ZombieDefenseInfo;
import io.netty.channel.Channel;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.MAX_PLAYERS_PER_TEAM;
import static com.fullsteam.Config.ZOMBIE_INITIAL_WAVE_DELAY_MS;
import static com.fullsteam.Config.ZOMBIE_TIME_BETWEEN_WAVES_MS;

/**
 * A cooperative PvE game mode where human players (Team 1) defend against
 * waves of AI-controlled zombies (Team 2).
 */
@GameName("Zombie Defense")
public class ZombieDefenseManager extends AbstractGameStateManager {

    private static final long WAVE_WARNING_TIME_MS = 5_000; // 5 seconds before the wave hits
    private int waveNumber = 0;
    private long nextWaveTime;
    private long roundEndTime;

    public ZombieDefenseManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public Player addPlayer(long playerId, Channel channel) {
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

        // clear the zombies from the previous round
        players.values()
                .stream()
                .filter(p -> p.getTeam() == 2)
                .map(Player::getId)
                .toList()
                .forEach(this::removePlayer);
        // Schedule the first wave
        this.nextWaveTime = System.currentTimeMillis() + ZOMBIE_INITIAL_WAVE_DELAY_MS;
        log.info("Zombie Defense match started. Survive for {} seconds.", Config.ROUND_DURATION_SECONDS);
        sendGameEvent(GameEvent.yellow("First wave incoming..."));
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        if (System.currentTimeMillis() >= nextWaveTime) {
            spawnNextWave();
        }
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (victim.getTeam() == 2) {
            removePlayer(victim.getId());
        }
    }

    @Override
    protected void applyPowerUp(Player player, PowerUp powerUp) {
        // Zombies (Team 2) cannot pick up power-ups.
        if (player.getTeam() == 2) {
            return; // Do nothing if a zombie touches a power-up
        }
        // If it's a human player, let the default logic handle it.
        super.applyPowerUp(player, powerUp);
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
            schedule(() -> {
                // Check if the game is still running to avoid sending messages after game over
                if (System.currentTimeMillis() < roundEndTime && !isRoundOver) {
                    sendGameEvent(GameEvent.yellow("Wave " + nextWaveNumber + " is incoming!"));
                }
            }, warningDelay);
        }
    }

    private void spawnZombie() {
        long playerId = Config.ID_COUNTER.incrementAndGet();
        // Zombies are on Team 2
        AIPlayer zombie;
        double random = ThreadLocalRandom.current().nextDouble();

        // Introduce special zombies in later waves
        if (waveNumber > 5 && random < 0.15) { // 15% chance for a Brute
            zombie = new ZombiePlayer(playerId, 0, 0, 2, Config.ZOMBIE_SPEED - .05);
            zombie.setPlayerName("Brute");
            zombie.setWeapon(WeaponFactory.HEAVY_ZOMBIE_CLAW);
            zombie.setMaxHp(300);
        } else if (waveNumber > 3 && random < 0.30) { // 30% chance for a Runner
            zombie = new ZombiePlayer(playerId, 0, 0, 2, Config.ZOMBIE_SPEED + .05);
            zombie.setPlayerName("Runner");
            zombie.setWeapon(WeaponFactory.ZOMBIE_CLAW);
            zombie.setMaxHp(50);
        } else {
            zombie = new ZombiePlayer(playerId, 0, 0, 2, Config.ZOMBIE_SPEED);
            zombie.setPlayerName("Zombie");
            zombie.setWeapon(WeaponFactory.ZOMBIE_CLAW);
            zombie.setMaxHp(50);
        }
        zombie.setDefaultSpeed(zombie.getSpeed());
        zombie.resetHp();
        // Spawn zombies at the edges of the map
        setZombieSpawnPosition(zombie);
        players.put(playerId, zombie);
    }

    private void setZombieSpawnPosition(Player zombie) {
        // Zombies now only spawn along the top edge of the map.
        double x = ThreadLocalRandom.current().nextDouble(Config.GAME_WIDTH);
        double y = 10; // Spawn near the top
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
        if (players.isEmpty()) {
            return false;
        }
        long humansAlive = players.values().stream()
                .filter(p -> p.getTeam() == 1 && !p.isDead())
                .count();
        // Use an if / else-if structure to prevent incorrect win conditions
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
        // Create a "bunker" at the bottom of the map.
        double houseWidth = 350;
        double houseHeight = 250;
        double wallThickness = 15;
        double doorSize = 60;

        double centerX = Config.GAME_WIDTH / 2.0;
        // Move the house to be against the bottom of the screen
        double bottom = Config.GAME_HEIGHT - 20;
        double top = bottom - houseHeight;
        double left = centerX - houseWidth / 2;
        double right = centerX + houseWidth / 2;


        // Top wall (with a door gap)
        obstacles.add(Obstacle.createRectangle(left, top, (houseWidth - doorSize) / 2, wallThickness));
        obstacles.add(Obstacle.createRectangle(centerX + doorSize / 2, top, (houseWidth - doorSize) / 2, wallThickness));

        // Bottom wall (solid)
        obstacles.add(Obstacle.createRectangle(left, bottom - wallThickness, houseWidth, wallThickness));

        // Left wall
        obstacles.add(Obstacle.createRectangle(left, top, wallThickness, houseHeight));
        // Right wall
        obstacles.add(Obstacle.createRectangle(right - wallThickness, top, wallThickness, houseHeight));

        log.info("Generated a bunker structure for Zombie Defense.");
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        // Human players should spawn inside the bunker
        if (player.getTeam() == 1) {
            double houseWidth = 300;
            double houseHeight = 200;
            double centerX = Config.GAME_WIDTH / 2.0;
            double bottom = Config.GAME_HEIGHT - 20;
            double centerY = bottom - houseHeight / 2.0;

            player.setX(centerX + (ThreadLocalRandom.current().nextDouble() - 0.5) * (houseWidth - 100));
            player.setY(centerY + (ThreadLocalRandom.current().nextDouble() - 0.5) * (houseHeight - 100));
        }
    }

    @Override
    public int getMaxPlayers() {
        return MAX_PLAYERS_PER_TEAM; // Only 5 humans allowed in this game
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        long timeToNextWave = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(nextWaveTime - System.currentTimeMillis()));
        long zombiesAlive = players.values().stream().filter(p -> p.getTeam() == 2 && !p.isDead()).count();
        return new ZombieDefenseInfo(
                this.waveNumber,
                zombiesAlive,
                timeToNextWave,
                roundTimeRemainingSeconds
        );
    }
}
