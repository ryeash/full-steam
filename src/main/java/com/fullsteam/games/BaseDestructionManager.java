package com.fullsteam.games;

import com.fullsteam.CollisionUtils;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.Base;

import com.fullsteam.model.Crate;
import com.fullsteam.model.Explosion;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Player;
import com.fullsteam.model.Targetable;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.gamemodes.BaseDestructionInfo;
import com.fullsteam.model.gamemodes.GameInfo;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_AREA_PADDING;
import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_HEALTH;
import static com.fullsteam.Config.BASE_DESTRUCTION_BASE_RADIUS;
import static com.fullsteam.Config.GAME_HEIGHT;
import static com.fullsteam.Config.GAME_WIDTH;

/**
 * Base Destruction game mode.
 * Team 1 (Attackers) tries to destroy Team 2's (Defenders) base before time runs out.
 * If the base is destroyed, Team 1 wins. If time runs out with the base intact, Team 2 wins.
 */
public class BaseDestructionManager extends AbstractTeamBasedManager {

    private static final Logger log = LoggerFactory.getLogger(BaseDestructionManager.class);
    
    private Base defendingBase;
    private boolean baseDestroyed = false;
    private boolean sent10SecondWarning = false;

    public BaseDestructionManager(GameLobby gameLobby) {
        super(gameLobby);
        log.info("Base Destruction game mode initialized.");
        generateBasePosition();
    }

    @Override
    public Player addPlayer(long playerId, Channel channel) {
        // In Base Destruction mode, Team 1 are attackers, Team 2 are defenders
        // Try to balance teams but prefer defenders (Team 2) if both teams are equal
        long team1Count = players.values().stream().filter(p -> p.getTeam() == 1).count();
        long team2Count = players.values().stream().filter(p -> p.getTeam() == 2).count();
        
        int team;
        if (team1Count < Config.MAX_PLAYERS_PER_TEAM && team2Count < Config.MAX_PLAYERS_PER_TEAM) {
            // If both teams have space, slightly favor defenders (Team 2)
            team = (team2Count <= team1Count) ? 2 : 1;
        } else if (team1Count < Config.MAX_PLAYERS_PER_TEAM) {
            team = 1; // Only Team 1 has space
        } else if (team2Count < Config.MAX_PLAYERS_PER_TEAM) {
            team = 2; // Only Team 2 has space
        } else {
            // Both teams are full, assign randomly
            team = ThreadLocalRandom.current().nextBoolean() ? 1 : 2;
        }
        
        return addPlayer(playerId, channel, team);
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();
        baseDestroyed = false;
        sent10SecondWarning = false;
        generateBasePosition();
        
        // Send role announcements
        sendGameEvent(GameEvent.team(1, "Team 1: DESTROY the enemy base before time runs out!"));
        sendGameEvent(GameEvent.team(2, "Team 2: DEFEND your base until time runs out!"));
        sendGameEvent(GameEvent.info("Base health: " + (int) defendingBase.getHp()));
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        updateBase();
    }

    private void updateBase() {
        if (defendingBase != null && !baseDestroyed && defendingBase.isDestroyed()) {
            baseDestroyed = true;
            sendGameEvent(GameEvent.team(1, "BASE DESTROYED! Team 1 (Attackers) wins!"));
            sendGameEvent(GameEvent.team(2, "Your base was destroyed! Team 2 (Defenders) lost!"));
            log.info("Base destroyed! Team 1 wins the round.");
        }
    }

    @Override
    protected void populateSpatialGrids() {
        super.populateSpatialGrids();
        if (defendingBase != null && !defendingBase.isDestroyed()) {
            double size = defendingBase.getRadius() * 2;
            targetGrid.insert(defendingBase, 
                             defendingBase.getX() - defendingBase.getRadius(), 
                             defendingBase.getY() - defendingBase.getRadius(), 
                             size, size);
        }
    }

    @Override
    protected void updateBullets(long delta) {
        bullets.removeIf(bullet -> {
            // Store the previous position for line-segment collision checks
            Vector2D oldPos = new Vector2D(bullet.getX(), bullet.getY());
            bullet.update(delta);
            Vector2D newPos = new Vector2D(bullet.getX(), bullet.getY());

            // Check bullet-target collisions using the line segment
            Set<Targetable> nearby = targetGrid.getNearby(oldPos, newPos);

            for (Targetable target : nearby) {
                if (target instanceof Player player) {
                    // Check for collision with an enemy player
                    if (!player.isDead() && player.getTeam() != bullet.getTeam()) {
                        Vector2D playerCenter = player.position();
                        if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, playerCenter, Config.PLAYER_RADIUS + 2)) {
                            Player shooter = players.get(bullet.getShooterId());
                            if (player.takeDamage(bullet.getDamage())) {
                                killPlayer(player, shooter);
                            }
                            applyBulletDestructionEffect(bullet, player);
                            return true;
                        }
                    }
                } else if (target instanceof Turret turret) {
                    if (turret.getTeam() != bullet.getTeam()) {
                        Vector2D position = turret.position();
                        if (CollisionUtils.checkLineCircleCollision(oldPos, newPos, position, turret.getRadius())) {
                            turret.takeDamage(bullet.getDamage());
                            applyBulletDestructionEffect(bullet, target);
                            return true;
                        }
                    }
                } else if (target instanceof Crate crate) {
                    if (!crate.isDestroyed() && CollisionUtils.checkLinePolygonCollision(oldPos, newPos, crate)) {
                        crate.takeDamage(bullet.getDamage());
                        applyBulletDestructionEffect(bullet, crate);
                        return true;
                    }
                } else if (target instanceof Base base) {
                    // Only Team 1 (attackers) can damage the base
                    if (bullet.getTeam() == 1 && !base.isDestroyed()) {
                        if (CollisionUtils.checkLinePolygonCollision(oldPos, newPos, base)) {
                            double previousHp = base.getHp();
                            base.takeDamage(bullet.getDamage());
                            double newHp = base.getHp();
                            
                            // Send damage feedback
                            if (newHp < previousHp) {
                                double healthPercentage = base.getHealthPercentage();
                                sendGameEvent(GameEvent.red("Base taking damage! Health: " + (int)(healthPercentage * 100) + "%"));
                                
                                // Critical health warning
                                if (healthPercentage <= 0.25 && previousHp / base.getMaxHp() > 0.25) {
                                    sendGameEvent(GameEvent.red("WARNING: Base health is CRITICAL!"));
                                }
                            }
                            
                            applyBulletDestructionEffect(bullet, base);
                            return true;
                        }
                    }
                } else if (target != null) {
                    throw new UnsupportedOperationException("Unsupported target type: " + target);
                }
            }

            // Check bullet-obstacle collisions using the line segment
            for (Obstacle obstacle : obstacles) {
                if (CollisionUtils.checkLinePolygonCollision(oldPos, newPos, obstacle)) {
                    applyBulletDestructionEffect(bullet, obstacle);
                    return true;
                }
            }

            if (bullet.hasExceededMaxDistance() || bullet.getSpeed() < 10) {
                applyBulletDestructionEffect(bullet, null);
                return true;
            }

            // Remove bullets that move out of bounds
            return newPos.x() < 0
                    || newPos.x() > Config.GAME_WIDTH
                    || newPos.y() < 0
                    || newPos.y() > Config.GAME_HEIGHT;
        });
    }

    @Override
    protected void applyLaser(LaserBlast laserBlast) {
        // Check obstacle collisions first
        for (Obstacle obstacle : obstacles) {
            Vector2D collision = CollisionUtils.findLineObstacleCollision(laserBlast.getStart(), laserBlast.getEnd(), obstacle);
            if (collision != null) {
                double currentDistanceSq = laserBlast.getStart().distanceSquared(laserBlast.getEnd());
                if (laserBlast.getStart().distanceSquared(collision) < currentDistanceSq) {
                    laserBlast.setEnd(collision);
                }
            }
        }

        // Check target collisions
        Set<Targetable> nearby = targetGrid.getNearby(laserBlast.getStart(), laserBlast.getEnd());

        for (Targetable target : nearby) {
            if (target instanceof Player player) {
                if (!player.isDead() && player.getTeam() != laserBlast.getTeam()) {
                    Vector2D playerCenter = player.position();
                    if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), playerCenter, Config.PLAYER_RADIUS)) {
                        Player shooter = players.get(laserBlast.getShooterId());
                        if (player.takeDamage(laserBlast.getDamage())) {
                            killPlayer(player, shooter);
                        }
                    }
                }
            } else if (target instanceof Turret turret) {
                if (turret.getTeam() != laserBlast.getTeam()) {
                    Vector2D position = turret.position();
                    if (CollisionUtils.checkLineCircleCollision(laserBlast.getStart(), laserBlast.getEnd(), position, turret.getRadius())) {
                        turret.takeDamage(laserBlast.getDamage());
                    }
                }
            } else if (target instanceof Crate crate) {
                if (!crate.isDestroyed() && CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), crate)) {
                    crate.takeDamage(laserBlast.getDamage());
                }
            } else if (target instanceof Base base) {
                // Only Team 1 (attackers) can damage the base
                if (laserBlast.getTeam() == 1 && !base.isDestroyed()) {
                    if (CollisionUtils.checkLinePolygonCollision(laserBlast.getStart(), laserBlast.getEnd(), base)) {
                        double previousHp = base.getHp();
                        base.takeDamage(laserBlast.getDamage());
                        double newHp = base.getHp();
                        
                        // Send damage feedback
                        if (newHp < previousHp) {
                            double healthPercentage = base.getHealthPercentage();
                            sendGameEvent(GameEvent.red("Base taking laser damage! Health: " + (int)(healthPercentage * 100) + "%"));
                            
                            // Critical health warning
                            if (healthPercentage <= 0.25 && previousHp / base.getMaxHp() > 0.25) {
                                sendGameEvent(GameEvent.red("WARNING: Base health is CRITICAL!"));
                            }
                        }
                    }
                }
            } else if (target != null) {
                throw new UnsupportedOperationException("Unsupported target type: " + target);
            }
        }
    }

    @Override
    protected void updateExplosion(Explosion explosion) {
        // Apply damage for new explosions that haven't dealt it yet
        if (!explosion.hasDamageBeenApplied()) {
            Vector2D explosionCenter = new Vector2D(explosion.getX(), explosion.getY());
            double radiusSq = explosion.getRadius() * explosion.getRadius();
            Player shooter = players.get(explosion.getShooterId());

            Set<Targetable> nearbyTargets = targetGrid.getNearby(explosion.position(), explosion.getRadius());
            for (Targetable t : nearbyTargets) {
                if (t instanceof Player p) {
                    if (p.isDead()) {
                        continue;
                    }
                    // Prevent friendly fire, but allow self-damage
                    if (shooter != null && p.getTeam() == shooter.getTeam() && !Objects.equals(p.getId(), shooter.getId())) {
                        continue;
                    }
                    if (p.position().distanceSquared(explosionCenter) < radiusSq) {
                        if (p.takeDamage(explosion.getDamage())) {
                            killPlayer(p, shooter);
                        }
                    }
                } else if (t instanceof Turret turret) {
                    // Prevent friendly fire, but allow self-damage
                    if (shooter != null && turret.getTeam() == shooter.getTeam() && !Objects.equals(turret.getId(), shooter.getId())) {
                        continue;
                    }
                    if (turret.position().distanceSquared(explosionCenter) < radiusSq) {
                        turret.takeDamage(explosion.getDamage());
                    }
                } else if (t instanceof Crate crate) {
                    if (CollisionUtils.checkCirclePolygonCollision(
                            explosionCenter,
                            explosion.getRadius(),
                            crate.getVertices())) {
                        crate.takeDamage(explosion.getDamage());
                    }
                } else if (t instanceof Base base) {
                    // Only Team 1 (attackers) explosions can damage the base
                    if (explosion.getTeam() == 1 && !base.isDestroyed()) {
                        if (CollisionUtils.checkCirclePolygonCollision(
                                explosionCenter,
                                explosion.getRadius(),
                                base.vertices())) {
                            double previousHp = base.getHp();
                            base.takeDamage(explosion.getDamage());
                            double newHp = base.getHp();
                            
                            // Send damage feedback
                            if (newHp < previousHp) {
                                double healthPercentage = base.getHealthPercentage();
                                sendGameEvent(GameEvent.red("Base hit by explosion! Health: " + (int)(healthPercentage * 100) + "%"));
                                
                                // Critical health warning
                                if (healthPercentage <= 0.25 && previousHp / base.getMaxHp() > 0.25) {
                                    sendGameEvent(GameEvent.red("WARNING: Base health is CRITICAL!"));
                                }
                            }
                        }
                    }
                } else if (t != null) {
                    throw new UnsupportedOperationException("Unsupported target type: " + t);
                }
            }
            explosion.markDamageApplied(); // Mark it so damage isn't applied again
        }
    }

    @Override
    protected boolean checkEndConditions() {
        boolean roundTimerExpired = System.currentTimeMillis() >= roundEndTime;
        
        if (baseDestroyed) {
            // Team 1 (Attackers) win
            if (!sentVictoryMessage) {
                team1Score++;
                sendVictoryMessage();
            }
            return true;
        } else if (roundTimerExpired) {
            // Team 2 (Defenders) win by successfully defending
            if (!sentVictoryMessage) {
                team2Score++;
                sendGameEvent(GameEvent.team(2, "TIME'S UP! Team 2 (Defenders) successfully defended the base!"));
                sendGameEvent(GameEvent.team(1, "Time ran out! Team 1 (Attackers) failed to destroy the base!"));
                sendVictoryMessage();
            }
            return true;
        }
        
        // Send 10-second warning
        if (!sent10SecondWarning && (roundEndTime - System.currentTimeMillis()) <= 10000) {
            sent10SecondWarning = true;
            sendGameEvent(GameEvent.red("10 seconds remaining! Defenders, hold the line!"));
        }
        
        return false;
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new BaseDestructionInfo(defendingBase, roundTimeRemainingSeconds, baseDestroyed);
    }

    @Override
    protected void generateObstacles() {
        // Generate obstacles but ensure they don't overlap with the base
        generateObstacles(newObstacle -> {
            if (defendingBase == null) {
                return true; // No base to check against yet
            }
            // Ensure obstacles don't overlap with the base area
            return !CollisionUtils.checkCirclePolygonCollision(
                defendingBase.position(), 
                defendingBase.getRadius() + 20, // Extra padding
                newObstacle.vertices()
            );
        });
    }

    private void generateBasePosition() {
        // Place the base on Team 2's (defenders) side of the map
        // Similar to CTF base positioning but only one base
        double baseX = ThreadLocalRandom.current().nextDouble(
            (GAME_WIDTH / 2.0) + BASE_DESTRUCTION_BASE_AREA_PADDING, 
            GAME_WIDTH - BASE_DESTRUCTION_BASE_AREA_PADDING
        );
        double baseY = ThreadLocalRandom.current().nextDouble(
            BASE_DESTRUCTION_BASE_AREA_PADDING, 
            GAME_HEIGHT - BASE_DESTRUCTION_BASE_AREA_PADDING
        );
        
        Vector2D basePosition = new Vector2D(baseX, baseY);
        this.defendingBase = new Base(
            Config.ID_COUNTER.incrementAndGet(),
            basePosition,
            BASE_DESTRUCTION_BASE_RADIUS,
            BASE_DESTRUCTION_BASE_HEALTH,
            2 // Team 2 (Defenders)
        );
        
        log.info("Generated base at position ({}, {}) with {} health", 
                 baseX, baseY, BASE_DESTRUCTION_BASE_HEALTH);
    }

    @Override
    protected void setValidSpawnPosition(Player player) {
        boolean invalidPosition;
        do {
            invalidPosition = false;
            double x, y;

            if (player.getTeam() == 1) {
                // Team 1 (Attackers) spawn on the left side
                double spawnableWidth = (GAME_WIDTH / 2.0) - Config.SPAWN_HORIZONTAL_PADDING - Config.SPAWN_MIDFIELD_BUFFER;
                x = Config.SPAWN_HORIZONTAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableWidth;
            } else {
                // Team 2 (Defenders) spawn near their base on the right side, but not too close to the base
                double minX = (GAME_WIDTH / 2.0) + Config.SPAWN_MIDFIELD_BUFFER;
                double maxX = GAME_WIDTH - Config.SPAWN_HORIZONTAL_PADDING;
                x = minX + ThreadLocalRandom.current().nextDouble() * (maxX - minX);
            }

            double spawnableHeight = GAME_HEIGHT - (2 * Config.SPAWN_VERTICAL_PADDING);
            y = Config.SPAWN_VERTICAL_PADDING + ThreadLocalRandom.current().nextDouble() * spawnableHeight;

            player.setX(x);
            player.setY(y);

            // Check if spawn point is inside an obstacle
            if (isColliding(player, obstacles)) {
                invalidPosition = true;
                continue;
            }

            // For Team 2 (Defenders), ensure they don't spawn too close to their own base
            if (player.getTeam() == 2 && defendingBase != null) {
                double distanceToBase = player.position().distance(defendingBase.position());
                if (distanceToBase < BASE_DESTRUCTION_BASE_RADIUS + 40) { // 40 pixels minimum distance
                    invalidPosition = true;
                }
            }
        } while (invalidPosition);
    }

    @Override
    protected void sendVictoryMessage() {
        if (sentVictoryMessage) {
            return;
        }
        sentVictoryMessage = true;
        
        if (baseDestroyed) {
            sendGameEvent(GameEvent.team(1, "Team 1 (Attackers) wins! Score: %d-%d".formatted((int) team1Score, (int) team2Score)));
        } else {
            sendGameEvent(GameEvent.team(2, "Team 2 (Defenders) wins! Score: %d-%d".formatted((int) team1Score, (int) team2Score)));
        }
    }
}
