package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Crate;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.gamemodes.BuilderGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static com.fullsteam.Config.PLAYER_RADIUS;

public class BuilderManager extends AbstractFreeForAllManager {

    private static final double CRATE_SIZE = 30.0;
    private static final double PLACEMENT_DISTANCE = CRATE_SIZE * 2;

    private final List<Crate> crates = Collections.synchronizedList(new LinkedList<>());

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
    protected GameInfo buildGameInfo() {
        return new BuilderGameInfo(crates);
    }

    @Override
    protected void populateSpatialGrids() {
        super.populateSpatialGrids();
        for (Crate crate : crates) {
            targetGrid.insert(crate, crate.getX(), crate.getY(), crate.getSize(), crate.getSize());
        }
    }

    @Override
    protected boolean checkEndConditions() {
        // No end conditions for now
        return false;
    }

    @Override
    public void handlePlayerInput(Long playerId, PlayerInput input) {
        super.handlePlayerInput(playerId, input);
        if (input.isPlacingObstacle()) {
            Player player = players.get(playerId);
            if (player != null) {
                placeCrate(playerId);
            }
        }
    }

    @Override
    protected void updatePlayers(long delta) {
        crates.removeIf(Crate::isDestroyed);
        // Temporarily add crates as obstacles for collision detection purposes.
        // This allows us to reuse the collision logic from the superclass.
        obstacles.addAll(crates);

        try {
            // Now the super method will handle collision with both permanent obstacles and crates.
            super.updatePlayers(delta);
        } finally {
            // Clean up the temporary crate obstacles to ensure they don't persist.
            obstacles.removeAll(crates);
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

        double dx = input.getMouseX() - player.getX();
        double dy = input.getMouseY() - player.getY();
        double length = Math.sqrt(dx * dx + dy * dy);
        double baseAngle = (length > 0.1) ? Math.atan2(dy, dx) : 0.0D;

        // Calculate the ideal placement location.
        double idealX = player.getX() + PLAYER_RADIUS - (CRATE_SIZE / 2.0) + Math.cos(baseAngle) * PLACEMENT_DISTANCE;
        double idealY = player.getY() + PLAYER_RADIUS - (CRATE_SIZE / 2.0) + Math.sin(baseAngle) * PLACEMENT_DISTANCE;

        // Snap the initial target position to the grid.
        double snappedX = Math.round(idealX / CRATE_SIZE) * CRATE_SIZE;
        double snappedY = Math.round(idealY / CRATE_SIZE) * CRATE_SIZE;

        Crate crate = new Crate(playerId, snappedX, snappedY, CRATE_SIZE, Config.BUILDER_CRATE_HEALTH);
        if (!isCollidingWithAnyCrate(crate) && !isCollidingWithAnyPlayer(crate)) {
            crates.add(crate);
            targetGrid.insert(crate, crate.getX(), crate.getY(), crate.getSize(), crate.getSize());
        }
    }

    private boolean isCollidingWithAnyCrate(Crate newCrate) {
        for (Crate existingCrate : crates) {
            if (newCrate.getX() < existingCrate.getX() + existingCrate.getSize() &&
                newCrate.getX() + CRATE_SIZE > existingCrate.getX() &&
                newCrate.getY() < existingCrate.getY() + existingCrate.getSize() &&
                newCrate.getY() + CRATE_SIZE > existingCrate.getY()) {
                return true;
            }
        }
        return false;
    }

    private boolean isCollidingWithAnyPlayer(Crate newCrate) {
        for (Player player : players.values()) {
            if (CollisionUtils.checkCirclePolygonCollision(player.position(), PLAYER_RADIUS, newCrate.getVertices())) {
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
