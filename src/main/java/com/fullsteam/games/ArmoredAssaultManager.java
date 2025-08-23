package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Vehicle;
import com.fullsteam.model.gamemodes.ArmoredAssaultInfo;
import com.fullsteam.model.gamemodes.GameInfo;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ArmoredAssaultManager extends AbstractTeamBasedManager {

    public ArmoredAssaultManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();

        // Clear existing vehicles
        vehicleManager.resetVehicles();
        entities.getVehicles().clear();

        for (Vehicle.VehicleType type : Vehicle.VehicleType.values()) {
            Vehicle vehicle1 = vehicleManager.spawnVehicle(type);
            // set a random location on the left
            vehicle1.setPosition(new Vector2D(
                    ThreadLocalRandom.current().nextDouble(50, ((double) Config.GAME_WIDTH / 2) - 100),
                    ThreadLocalRandom.current().nextDouble(50, Config.GAME_HEIGHT - 50)));
            // rotate the first position 180
            Vehicle vehicle2 = vehicleManager.spawnVehicle(type);
            vehicle2.setPosition(new Vector2D(
                    Config.GAME_WIDTH - vehicle1.position().x(),
                    Config.GAME_HEIGHT - vehicle1.position().y()));
        }
    }

    @Override
    protected void generateObstacles() {
        Obstacle randomPolygonObstacle = Obstacle.createRandomPolygonObstacle();
        randomPolygonObstacle.setPosition(new Vector2D((double) Config.GAME_WIDTH / 2, (double) Config.GAME_HEIGHT / 2));
        entities.getObstacles().add(randomPolygonObstacle);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (shooter != null) {
            if (shooter.getTeam() == 1) {
                team1Score++;
            } else {
                team2Score++;
            }
        }
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new ArmoredAssaultInfo(
                team1Score,
                team2Score,
                timeLeft);
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() >= roundEndTime) {
            sendVictoryMessage();
            log.info("Round timer has expired. Starting a new round.");
            return true;
        } else {
            return false;
        }
    }
}