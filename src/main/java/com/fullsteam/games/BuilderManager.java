package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Crate;
import com.fullsteam.model.Player;
import com.fullsteam.model.gamemodes.BuilderGameInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.netty.channel.Channel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class BuilderManager extends AbstractGameStateManager {

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
        // Start with an empty map, no static obstacles
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
                if (bullet.getX() >= crate.getX() - crate.getSize() / 2 &&
                    bullet.getX() <= crate.getX() + crate.getSize() / 2 &&
                    bullet.getY() >= crate.getY() - crate.getSize() / 2 &&
                    bullet.getY() <= crate.getY() + crate.getSize() / 2) {
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

        long ownedCrates = crates.stream()
                .filter(c -> playerId.equals(c.getOwnerId()))
                .count();

        if (ownedCrates < Config.BUILDER_MAX_OBSTACLES) {
            double crateX = player.getX() + Math.cos(player.getAngle()) * 40;
            double crateY = player.getY() + Math.sin(player.getAngle()) * 40;
            Crate newCrate = new Crate(playerId, crateX, crateY, 30, Config.BUILDER_CRATE_HEALTH);
            crates.add(newCrate);
        }
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
