package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.GameInfo;
import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BuilderManager extends AbstractGameStateManager {

    private final AtomicInteger teamIdCounter = new AtomicInteger(100);

    public BuilderManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Builder";
    }

    @Override
    protected void generateObstacles() {
        // Start with an empty map
    }

    @Override
    protected GameInfo buildGameState() {
        // For now, we'll return a null GameInfo. We'll implement this later.
        return null;
    }

    @Override
    protected boolean checkEndConditions() {
        // No end conditions for now
        return false;
    }

    @Override
    public Player addPlayer(String playerId, Channel channel) {
        // In Builder mode, every player is on their own team.
        int uniqueTeamId = teamIdCounter.getAndIncrement();
        Player player = new Player(playerId, 0, 0, uniqueTeamId);
        setValidSpawnPosition(player);
        players.put(playerId, player);
        playerChannels.put(playerId, channel);
        log.info("Player {} joined Builder game {} at position ({}, {})", playerId, gameId, player.getX(), player.getY());
        return player;
    }

    public void placeObstacle(String playerId) {
        Player player = players.get(playerId);
        if (player == null) {
            return;
        }

        long ownedObstacles = obstacles.stream()
                .filter(o -> playerId.equals(o.getOwnerId()))
                .count();

        if (ownedObstacles < Config.BUILDER_MAX_OBSTACLES) {
            double obstacleX = player.getX() + Math.cos(player.getAngle()) * 40;
            double obstacleY = player.getY() + Math.sin(player.getAngle()) * 40;
            Obstacle newObstacle = Obstacle.createRectangle(obstacleX, obstacleY, 30, 30, playerId);
            obstacles.add(newObstacle);
        }
    }


    @Override
    protected void killPlayer(Player victim, Player shooter) {
        removePlayerObstacles(victim.getId());
        super.killPlayer(victim, shooter);
    }

    @Override
    public void removePlayer(String playerId) {
        removePlayerObstacles(playerId);
        super.removePlayer(playerId);
    }

    private void removePlayerObstacles(String playerId) {
        List<Obstacle> obstaclesToRemove = obstacles.stream()
                .filter(o -> playerId.equals(o.getOwnerId()))
                .toList();
        obstacles.removeAll(obstaclesToRemove);
    }
}
