package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.Portal;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.PortalInfo;
import io.micronaut.context.annotation.Prototype;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;

@Prototype
public class PortalManager extends AbstractTeamBasedManager {

    private static final long PORTAL_COOLDOWN = 750;
    private static final double PORTAL_RADIUS = Config.PLAYER_RADIUS * 3;
    private final Map<Long, Long> lastPlayerPortalCreation = new ConcurrentSkipListMap<>();

    public PortalManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        applyDeathmatchScoring();
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new PortalInfo(
                team1Score,
                team2Score,
                timeLeft);
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() >= roundEndTime) {
            sendVictoryMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void additionalPlayerInput(Player player, PlayerInput playerInput) {
        if (playerInput.isAltFire() && lastPlayerPortalCreation.getOrDefault(player.id(), 0L) + PORTAL_COOLDOWN < System.currentTimeMillis()) {
            lastPlayerPortalCreation.put(player.id(), System.currentTimeMillis());
            launchPortal(player, playerInput.getMouseX(), playerInput.getMouseY());
        }
    }

    protected void launchPortal(Player player, double targetX, double targetY) {
        if (player.isDead()) {
            return;
        }
        double directionX = targetX - player.getX();
        double directionY = targetY - player.getY();
        double distance = Math.sqrt(directionX * directionX + directionY * directionY);
        if (distance > 0) {
            directionX /= distance;
            directionY /= distance;
            double portalRange = Math.min(Config.PLAYER_RADIUS * 2, distance);
            double portalX = player.getX() + directionX * portalRange;
            double portalY = player.getY() + directionY * portalRange;
            Portal portal = new Portal(
                    Config.ID_COUNTER.incrementAndGet(),
                    player.getTeam(),
                    portalX,
                    portalY,
                    PORTAL_RADIUS,
                    player.getId()
            );
            fieldEffectSystem.placePortal(portal);
        }
    }
}
