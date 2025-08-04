package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Crate;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.gamemodes.BuilderGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.fullsteam.Config.PLAYER_SIZE;

public class BuilderManager extends AbstractGameStateManager {

    private static final double CRATE_SIZE = 30.0;
    private static final double PLACEMENT_DISTANCE = CRATE_SIZE * 2;
    private static final double PLACEMENT_SEARCH_RADIUS = PLACEMENT_DISTANCE + 5;
    private static final int PLACEMENT_SEARCH_STEPS = 8;

    private final AtomicInteger teamIdCounter = new AtomicInteger(100);
    private final List<Crate> crates = new CopyOnWriteArrayList<>();

    public BuilderManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Builder";
    }

    @Override
    protected void generateObstacles() {
//        obstacles.add(Obstacle.createRectangle(
//                (Config.GAME_WIDTH - Config.ESCORT_OBSTACLE_WIDTH) / 2,
//                (Config.GAME_HEIGHT - Config.ESCORT_OBSTACLE_WIDTH) / 2,
//                Config.ESCORT_OBSTACLE_WIDTH,
//                Config.ESCORT_OBSTACLE_WIDTH));

    }

    @Override
    protected void generateHazards() {
        // no hazards
    }

    @Override
    protected GameInfo buildGameState() {
        return new BuilderGameInfo(crates);
    }

    @Override
    protected void updateBullets() {
        super.updateBullets();
        bullets.removeIf(bullet -> {
            for (Crate crate : crates) {
                // AABB collision check, assuming crate's (x,y) is its top-left corner.
                if (bullet.getX() >= crate.getX() &&
                    bullet.getX() <= crate.getX() + crate.getSize() &&
                    bullet.getY() >= crate.getY() &&
                    bullet.getY() <= crate.getY() + crate.getSize()) {
                    crate.takeDamage(bullet.getDamage());
                    if (crate.isDestroyed()) {
                        crates.remove(crate);
                    }
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    protected boolean checkEndConditions() {
        // No end conditions for now
        return false;
    }

    @Override
    public void handlePlayerInput(String playerId, PlayerInput input) {
        super.handlePlayerInput(playerId, input);
        // Handle weapon cycle with a 500ms cooldown
        if (input.isPlacingObstacle()) {
            Player player = players.get(playerId);
            if (player != null && player.getAlternateActionCooldown() < System.currentTimeMillis()) {
                player.setAlternateActionCooldown(System.currentTimeMillis() + 500);
                placeCrate(playerId);
            }
        }
    }

    @Override
    protected void updatePlayers() {
        // Temporarily add crates as obstacles for collision detection purposes.
        // This allows us to reuse the collision logic from the superclass.
        List<Obstacle> crateObstacles = new ArrayList<>();
        for (Crate crate : crates) {
            Obstacle o = Obstacle.createRectangle(crate.getX(), crate.getY(), crate.getSize(), crate.getSize());
            crateObstacles.add(o);
        }
        obstacles.addAll(crateObstacles);

        try {
            // Now the super method will handle collision with both permanent obstacles and crates.
            super.updatePlayers();
        } finally {
            // Clean up the temporary crate obstacles to ensure they don't persist.
            obstacles.removeAll(crateObstacles);
        }
    }

    @Override
    public Player addPlayer(String playerId, Channel channel) {
        int uniqueTeamId = teamIdCounter.getAndIncrement();
        Player player = new Player(playerId, 0, 0, uniqueTeamId);
        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined Builder game {} at position ({}, {})", playerId, gameId, player.getX(), player.getY());
        return player;
    }

    public void placeCrate(String playerId) {
        Player player = players.get(playerId);
        if (player == null) {
            return;
        }
        PlayerInput input = playerInput.get(playerId);
        if (input == null) {
            return;
        }

        long ownedCrates = crates.stream()
                .filter(c -> playerId.equals(c.getOwnerId()))
                .count();

        if (ownedCrates >= Config.BUILDER_MAX_OBSTACLES) {
            return;
        }

        double dx = input.getMouseX() - (player.getX() + PLAYER_SIZE / 2.0);
        double dy = input.getMouseY() - (player.getY() + PLAYER_SIZE / 2.0);
        double length = Math.sqrt(dx * dx + dy * dy);
        double baseAngle = (length > 0.1) ? Math.atan2(dy, dx) : player.getAngle();

        // Calculate the ideal placement location.
        double idealX = player.getX() + (PLAYER_SIZE / 2.0) - (CRATE_SIZE / 2.0) + Math.cos(baseAngle) * PLACEMENT_DISTANCE;
        double idealY = player.getY() + (PLAYER_SIZE / 2.0) - (CRATE_SIZE / 2.0) + Math.sin(baseAngle) * PLACEMENT_DISTANCE;

        // Snap the initial target position to the grid.
        double snappedX = Math.round(idealX / CRATE_SIZE) * CRATE_SIZE;
        double snappedY = Math.round(idealY / CRATE_SIZE) * CRATE_SIZE;

        if (!isCollidingWithAnyCrate(snappedX, snappedY)) {
            crates.add(new Crate(playerId, snappedX, snappedY, CRATE_SIZE, Config.BUILDER_CRATE_HEALTH));
            return;
        }

        // Use a Set to avoid checking the same grid cell multiple times.
        Set<String> checkedGridCells = new HashSet<>();
        checkedGridCells.add(snappedX + "," + snappedY);

        // If the initial spot is taken, search nearby grid cells.
        for (double r = CRATE_SIZE / 2; r <= PLACEMENT_SEARCH_RADIUS; r += CRATE_SIZE / 2) {
            for (int i = 0; i < PLACEMENT_SEARCH_STEPS; i++) {
                double angle = (2 * Math.PI / PLACEMENT_SEARCH_STEPS) * i;
                // Search from the original, non-snapped target for a smoother radial search.
                double checkX = idealX + r * Math.cos(angle);
                double checkY = idealY + r * Math.sin(angle);

                // Snap the potential position to the grid.
                double snappedCheckX = Math.round(checkX / CRATE_SIZE) * CRATE_SIZE;
                double snappedCheckY = Math.round(checkY / CRATE_SIZE) * CRATE_SIZE;

                String posKey = snappedCheckX + "," + snappedCheckY;
                if (checkedGridCells.contains(posKey)) {
                    continue; // Already checked this grid cell.
                }
                checkedGridCells.add(posKey);

                if (!isCollidingWithAnyCrate(snappedCheckX, snappedCheckY)) {
                    crates.add(new Crate(playerId, snappedCheckX, snappedCheckY, CRATE_SIZE, Config.BUILDER_CRATE_HEALTH));
                    return;
                }
            }
        }
    }

    private boolean isCollidingWithAnyCrate(double newCrateX, double newCrateY) {
        for (Crate existingCrate : crates) {
            if (newCrateX < existingCrate.getX() + existingCrate.getSize() &&
                newCrateX + CRATE_SIZE > existingCrate.getX() &&
                newCrateY < existingCrate.getY() + existingCrate.getSize() &&
                newCrateY + CRATE_SIZE > existingCrate.getY()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        removePlayerCrates(victim.getId());
        super.killPlayer(victim, shooter);
    }

    @Override
    public void removePlayer(String playerId) {
        removePlayerCrates(playerId);
        super.removePlayer(playerId);
    }

    private void removePlayerCrates(String playerId) {
        crates.removeIf(crate -> playerId.equals(crate.getOwnerId()));
    }
}
