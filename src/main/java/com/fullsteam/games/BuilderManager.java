package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Crate;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.gamemodes.BuilderGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.fullsteam.Config.PLAYER_SIZE;

@GameName("Builder")
public class BuilderManager extends AbstractFreeForAllManager {

    private static final double CRATE_SIZE = 30.0;
    private static final double PLACEMENT_DISTANCE = CRATE_SIZE * 2;
    private static final double PLACEMENT_SEARCH_RADIUS = PLACEMENT_DISTANCE + 5;
    private static final int PLACEMENT_SEARCH_STEPS = 8;

    private final List<Crate> crates = new CopyOnWriteArrayList<>();

    public BuilderManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected void balanceOpponents() {
        // do nothing
    }

    @Override
    protected void generateObstacles() {
        // builders make their own
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
    protected void updateBullets(long delta) {
        super.updateBullets(delta);
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
                    bullet.getOnDestructionAction()
                            .map(action -> action.apply(bullet))
                            .ifPresent(this::applyBulletEffect);
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
    public void handlePlayerInput(Long playerId, PlayerInput input) {
        super.handlePlayerInput(playerId, input);
        // Handle weapon cycle with a 500ms cooldown
        if (input.isPlacingObstacle()) {
            Player player = players.get(playerId);
            if (player != null) {
                placeCrate(playerId);
            }
        }
    }

    @Override
    protected void updatePlayers(long delta) {
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
            super.updatePlayers(delta);
        } finally {
            // Clean up the temporary crate obstacles to ensure they don't persist.
            obstacles.removeAll(crateObstacles);
        }
    }

    public void placeCrate(Long playerId) {
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
        double baseAngle = (length > 0.1) ? Math.atan2(dy, dx) : 0.0D;

        // Calculate the ideal placement location.
        double idealX = player.getX() + (PLAYER_SIZE / 2.0) - (CRATE_SIZE / 2.0) + Math.cos(baseAngle) * PLACEMENT_DISTANCE;
        double idealY = player.getY() + (PLAYER_SIZE / 2.0) - (CRATE_SIZE / 2.0) + Math.sin(baseAngle) * PLACEMENT_DISTANCE;

        // Snap the initial target position to the grid.
        double snappedX = Math.round(idealX / CRATE_SIZE) * CRATE_SIZE;
        double snappedY = Math.round(idealY / CRATE_SIZE) * CRATE_SIZE;

        if (!isCollidingWithAnyCrate(snappedX, snappedY) && !isCollidingWithAnyPlayer(snappedX, snappedY)) {
            crates.add(new Crate(playerId, snappedX, snappedY, CRATE_SIZE, Config.BUILDER_CRATE_HEALTH));
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

    private boolean isCollidingWithAnyPlayer(double crateX, double crateY) {
        for (Player player : players.values()) {
            // AABB collision check
            if (crateX < player.getX() + PLAYER_SIZE &&
                crateX + CRATE_SIZE > player.getX() &&
                crateY < player.getY() + PLAYER_SIZE &&
                crateY + CRATE_SIZE > player.getY()) {
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
    public void removePlayer(long playerId) {
        removePlayerCrates(playerId);
        super.removePlayer(playerId);
    }

    private void removePlayerCrates(Long playerId) {
        crates.removeIf(crate -> playerId.equals(crate.getOwnerId()));
    }
}
