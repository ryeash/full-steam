package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WeaponUpgrade;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.ProgressionInfo;
import io.micronaut.context.annotation.Prototype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Prototype
public class ProgressionManager extends AbstractFreeForAllManager {
    private static final Logger log = LoggerFactory.getLogger(ProgressionManager.class);

    // Weapon progression tiers (weakest to strongest)
    private static final Weapon[] WEAPON_TIERS = {
            WeaponFactory.SMG,              // Tier 0 - Starting weapon (weakest)
            WeaponFactory.ASSAULT,          // Tier 1
            WeaponFactory.SHOTGUN,          // Tier 2
            WeaponFactory.SNIPER_RIFLE,     // Tier 3
            WeaponFactory.LASER_PISTOL,     // Tier 4
            WeaponFactory.LASER_RIFLE       // Tier 5 - Best weapon
    };

    private static final double PICKUP_RADIUS = 30.0; // Distance to pick up weapon drops

    private final List<WeaponUpgrade> weaponUpgrades = Collections.synchronizedList(new LinkedList<>());

    public ProgressionManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    public void startNewRound() {
        super.startNewRound();
        weaponUpgrades.clear();

        // Equip all players with the starting weapon (SMG)
        for (Player player : entities.getPlayers()) {
            player.setWeapon(WEAPON_TIERS[0]);
            player.setAmmoInMag(WEAPON_TIERS[0].getRoundsPerMagazine());
        }

        sendGameEvent(GameEvent.info("Everyone starts with a pistol. Kill enemies to get better weapons!"));
        sendGameEvent(GameEvent.info("Walk over weapon drops to upgrade your arsenal!"));
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        updateWeaponDrops();
        handleWeaponPickups();
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (victim.id() == shooter.id()) {
            return;
        }
        createWeaponDrop(victim.position(), victim.getWeapon());
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        super.setValidSpawnPosition(player);

        // Always reset to starting weapon when spawning (including respawns after death)
        player.setWeapon(WEAPON_TIERS[0]);
        player.setAmmoInMag(WEAPON_TIERS[0].getRoundsPerMagazine());
    }

    @Override
    protected GameInfo buildGameInfo() {
        long timeLeft = Math.max(0, (roundEndTime - System.currentTimeMillis()) / 1000);
        return new ProgressionInfo(timeLeft, weaponUpgrades);
    }

    @Override
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        // Disallow weapon changes
        if (request.getWeaponName() != null && !request.getWeaponName().isEmpty()) {
            sendGameEvent(GameEvent.yellow("Weapon selection is disabled!", playerId));
            request.setWeaponName(null);
        }
        super.handlePlayerConfigChange(playerId, request);
    }

    private void createWeaponDrop(Vector2D position, Weapon weapon) {
        WeaponUpgrade drop = new WeaponUpgrade(position);
        weaponUpgrades.add(drop);
        log.debug("Created weapon drop: {} at {}", weapon.getName(), position);
    }

    private void updateWeaponDrops() {
        // Remove expired weapon drops
        weaponUpgrades.removeIf(WeaponUpgrade::isExpired);
    }

    private void handleWeaponPickups() {
        for (Player player : entities.getPlayers()) {
            if (player.isDead()) {
                continue;
            }

            Iterator<WeaponUpgrade> iterator = weaponUpgrades.iterator();
            while (iterator.hasNext()) {
                WeaponUpgrade drop = iterator.next();
                double distance = player.position().distance(drop.getPosition());

                if (distance <= PICKUP_RADIUS) {
                    Weapon weapon = player.getWeapon();

                    for (int i = 0, weaponTiersLength = WEAPON_TIERS.length; i < weaponTiersLength; i++) {
                        Weapon weaponTier = WEAPON_TIERS[i];
                        if (weapon.getName().equals(weaponTier.getName())) {
                            if (i + 1 < WEAPON_TIERS.length) {
                                player.setWeapon(WEAPON_TIERS[i + 1]);
                                sendGameEvent(GameEvent.yellow("Upgrading to " + player.getWeapon().getName(), player.id()));
                            }
                            break;
                        }
                    }
                    iterator.remove();
                    break; // Only pick up one weapon per frame
                }
            }
        }
    }
}
