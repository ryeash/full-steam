package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.WeaponFactory;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.UnifiedAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.InfectionInfo;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.websocket.WebSocketSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Infection game mode manager.
 * Survivors must outlast the infected until time runs out.
 * Infected must spread the virus to all survivors by eliminating them.
 * When an infected kills a survivor, the survivor immediately becomes infected.
 */
@Prototype
public class InfectionManager extends AbstractGameStateManager {

    private static final Logger log = LoggerFactory.getLogger(InfectionManager.class);

    // Configuration constants
    private static final int PREPARATION_TIME_MS = 10000; // 10 seconds preparation
    private static final int INITIAL_INFECTED_COUNT = 3; // Number of initial infected
    private static final double ZOMBIE_SPEED_MULTIPLIER = 1.3; // Zombies are faster
    private static final double ZOMBIE_HEALTH = 75.0; // Zombies have less health

    // AI balancing constants
    private static final int MAX_PLAYERS = Config.MAX_PLAYERS_PER_TEAM * 2; // Maximum total players
    private static final long AI_FILL_CHECK_INTERVAL_MS = 5000; // 5 seconds
    private static final double INFECTED_RATIO_TARGET = 0.25; // Target 25% infected initially

    // Game state tracking
    private final Set<Long> infectedPlayers = new HashSet<>();
    private boolean gameStarted = false;
    private long infectionStartTime = 0;
    private long roundEndTime = 0;
    private long lastAIFillCheckTime = 0;

    public InfectionManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    public PlayerSession addPlayer(long playerId, WebSocketSession channel) {
        // If the game is at max capacity, try to remove an AI to make room.
        if (entities.getPlayers().size() >= MAX_PLAYERS) {
            Optional<Player> aiToKick = entities.getPlayers().stream()
                    .filter(p -> p instanceof AIPlayer)
                    .findFirst();

            if (aiToKick.isPresent()) {
                removePlayer(aiToKick.get().getId());
                log.info("Kicking AI player {} to make room for human player {}.", aiToKick.get().getId(), playerId);
            } else {
                log.warn("Cannot add player {}: game is full of human players.", playerId);
                return null;
            }
        }

        // All players start as survivors (Team 1)
        return super.addPlayer(playerId, channel, 1);
    }

    @Override
    protected void updateGame(long delta) {
        super.updateGame(delta);
        long currentTime = System.currentTimeMillis();

        // Only check to add AI if the round is active
        if (!isRoundOver && currentTime - lastAIFillCheckTime > AI_FILL_CHECK_INTERVAL_MS) {
            balanceInfectionPlayers();
            lastAIFillCheckTime = currentTime;
        }
    }

    /**
     * Balances AI players to ensure good infection gameplay.
     * Prioritizes filling infected team when there are few players.
     */
    private void balanceInfectionPlayers() {
        int totalPlayers = entities.getPlayers().size();
        int humanPlayers = (int) entities.getPlayers().stream()
                .filter(p -> !(p instanceof AIPlayer))
                .count();

        // If we have enough human players, don't add AI
        if (humanPlayers >= MAX_PLAYERS) {
            return;
        }

        // Calculate how many AI to add
        int playersToAdd = MAX_PLAYERS - totalPlayers;

        if (playersToAdd > 0) {
            // During preparation phase, add AI as survivors first
            if (!gameStarted) {
                for (int i = 0; i < playersToAdd; i++) {
                    addInfectionAIPlayer(1); // Survivor team
                }
            } else {
                // During infection phase, prioritize infected team if they need more players
                int infectedCount = infectedPlayers.size();

                // Calculate ideal infected count (at least 1, target ratio of total)
                int idealInfectedCount = Math.max(1, (int) (totalPlayers * INFECTED_RATIO_TARGET));

                for (int i = 0; i < playersToAdd; i++) {
                    if (infectedCount < idealInfectedCount) {
                        // Add to infected team
                        AIPlayer ai = addInfectionAIPlayer(2);
                        // Make sure the AI is properly infected
                        infectedPlayers.add(ai.getId());
                        applyZombieAbilities(ai);
                        infectedCount++;
                    } else {
                        // Add to survivor team
                        AIPlayer ai = addInfectionAIPlayer(1);
                        applySurvivorAbilities(ai);
                    }
                }
            }
        } else if (playersToAdd < 0) {
            // Remove excess AI players if we have too many
            int playersToRemove = -playersToAdd;
            log.info("Infection: Removing {} excess AI players. Current: {}, Max: {}",
                    playersToRemove, totalPlayers, MAX_PLAYERS);

            List<Long> aiPlayerIdsToRemove = entities.getPlayers().stream()
                    .filter(p -> p instanceof AIPlayer)
                    .map(Player::getId)
                    .limit(playersToRemove)
                    .toList();

            for (Long playerId : aiPlayerIdsToRemove) {
                removePlayer(playerId);
                log.info("Removed AI Player {} to meet max player limit.", playerId);
            }
        }
    }

    @Override
    public AIPlayer addAIPlayer(int team) {
        return addInfectionAIPlayer(team);
    }

    /**
     * Adds an AI player to the infection game with appropriate abilities.
     */
    private AIPlayer addInfectionAIPlayer(int team) {
        long playerId = Config.ID_COUNTER.incrementAndGet();

        if (team == 2) {
            infectedPlayers.add(playerId);
        }

        // Create AI with appropriate archetype for infection mode
        AIArchetype archetype = team == 2 ?
                AIArchetype.WARRIOR : // Infected are aggressive
                AIArchetype.randomArchetype(); // Survivors get varied archetypes

        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new UnifiedAIStrategy(), archetype);
        setValidSpawnPosition(player);
        entities.addPlayer(new PlayerSession(this, player, null));

        log.info("AI Player {} joined Infection game on team {} at position ({}, {})",
                playerId, team, player.getX(), player.getY());
        return player;
    }

    @Override
    protected void startNewRound() {
        super.startNewRound();

        // Reset infection state
        infectedPlayers.clear();
        gameStarted = false;

        // Set round end time
        roundEndTime = System.currentTimeMillis() + (Config.ROUND_DURATION_SECONDS * 1000);
        infectionStartTime = System.currentTimeMillis() + PREPARATION_TIME_MS;

        // All players start as survivors during preparation phase
        entities.getPlayers().forEach(player -> {
            player.setTeam(1); // Survivor team
            applySurvivorAbilities(player);
        });

        // Send game messages
        sendGameEvent(GameEvent.yellow("Infection: " + PREPARATION_TIME_MS / 1000 + " seconds until infection begins!"));
        sendGameEvent(GameEvent.info("Survivors: Prepare for the outbreak!"));
        sendGameEvent(GameEvent.yellow("All players are survivors during preparation phase."));

        // Schedule infection start
        schedule(this::startInfection, PREPARATION_TIME_MS);
    }

    private void selectInitialInfected() {
        List<Player> players = new ArrayList<>(entities.getPlayers());
        Collections.shuffle(players);

        // Calculate number of initial infected (minimum 1, maximum 1/3 of players)
        int playerCount = players.size();
        int infectedCount = Math.max(1, Math.min(INITIAL_INFECTED_COUNT, playerCount / 3));

        // Prefer AI players for initial infection to make it more interesting for humans
        players.sort((p1, p2) -> {
            boolean p1IsAI = p1 instanceof AIPlayer;
            boolean p2IsAI = p2 instanceof AIPlayer;
            if (p1IsAI && !p2IsAI) {
                return -1; // AI first
            }
            if (!p1IsAI && p2IsAI) {
                return 1;  // Human second
            }
            return 0; // Same type, keep random order
        });

        for (int i = 0; i < infectedCount && i < players.size(); i++) {
            Player infected = players.get(i);
            infectedPlayers.add(infected.getId());
            infected.setTeam(2); // Infected team
            applyZombieAbilities(infected);

            String playerType = infected instanceof AIPlayer ? "AI" : "Human";
            sendGameEvent(GameEvent.red(infected.getName() + " (" + playerType + ") is patient zero!"));
        }

        log.info("Selected {} initial infected players from {} total players (AI preferred)", infectedCount, playerCount);
    }

    private void startInfection() {
        infectionStartTime = 0;
        gameStarted = true;
        selectInitialInfected();
        sendGameEvent(GameEvent.red("THE INFECTION HAS BEGUN!"));
        sendGameEvent(GameEvent.yellow("Survivors: Fight for your lives!"));
        sendGameEvent(GameEvent.info("Infected: Spread the virus to all survivors!"));
    }

    private void applySurvivorAbilities(Player survivor) {
        survivor.setWeapon(WeaponFactory.getRandomRangedWeapon());
        survivor.setMaxHp(Config.DEFAULT_PLAYER_HEALTH);
        survivor.setHp(Config.DEFAULT_PLAYER_HEALTH);
        survivor.setSpeed(Config.DEFAULT_PLAYER_SPEED); // Normal speed
        survivor.setDefaultSpeed(Config.DEFAULT_PLAYER_SPEED);
    }

    private void applyZombieAbilities(Player zombie) {
        zombie.setWeapon(WeaponFactory.HEAVY_ZOMBIE_CLAW);
        zombie.setMaxHp(ZOMBIE_HEALTH);
        zombie.setHp(ZOMBIE_HEALTH);
        double zombieSpeed = Config.DEFAULT_PLAYER_SPEED * ZOMBIE_SPEED_MULTIPLIER;
        zombie.setSpeed(zombieSpeed);
        zombie.setDefaultSpeed(zombieSpeed);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        if (!gameStarted) {
            // During preparation phase, use standard respawn
            super.killPlayer(victim, shooter);
            return;
        }
        // Check for infection spread
        if (shooter != null
            && infectedPlayers.contains(shooter.getId())
            && !infectedPlayers.contains(victim.getId())) {

            // INFECTION SPREAD!
            infectPlayer(victim, shooter);

            // Shooter gets a "kill" for scoring
            shooter.incrementKills();
        } else {
            super.killPlayer(victim, shooter);
        }
    }

    private void infectPlayer(Player victim, Player infector) {
        String victimType = victim instanceof AIPlayer ? "AI" : "Human";
        String infectorType = infector instanceof AIPlayer ? "AI" : "Human";
        log.info("Player {} ({}) infected by {} ({})", victim.getName(), victimType, infector.getName(), infectorType);
        infectedPlayers.add(victim.getId());
        victim.setTeam(2);
        applyZombieAbilities(victim);
        victim.setRespawnTime(System.currentTimeMillis() + Config.RESPAWN_DELAY_MS / 2);
        victim.setDead(true);
        victim.resetHp();
        setValidSpawnPosition(victim);

        sendGameEvent(GameEvent.red(victim.getName() + " (" + victimType + ") has been infected!"));
        sendGameEvent(GameEvent.team(1, "A survivor has fallen! " + entities.getTeamPlayerCount(1) + " remain!"));

        // Check for zombie victory
        if (entities.getTeamPlayerCount(1) <= 0) {
            sendGameEvent(GameEvent.team(2, "The infection is complete! Zombies win!"));
        }
    }

    @Override
    protected boolean checkEndConditions() {
        if (!gameStarted) {
            return false;
        }

        int survivorCount = entities.getTeamPlayerCount(1);
        // Zombies win if all survivors are infected
        if (survivorCount <= 0 && !infectedPlayers.isEmpty()) {
            return true;
        }

        // Survivors win if they outlast the time limit
        if (System.currentTimeMillis() >= roundEndTime) {
            if (survivorCount > 0) {
                sendGameEvent(GameEvent.team(1, "Survivors outlasted the infection! Humanity wins!"));
                log.info("Survivors achieved time-based victory. Remaining survivors: {}", survivorCount);
            } else {
                sendGameEvent(GameEvent.info("The infection consumed all humanity..."));
            }
            return true;
        }

        return false;
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new InfectionInfo(
                entities.getTeamPlayerCount(1),
                infectedPlayers.size(),
                timeLeft,
                gameStarted,
                infectionStartTime
        );
    }

    @Override
    public void handlePlayerConfigChange(Long playerId, PlayerConfigRequest request) {
        // Disallow weapon changes for zombies
        if (infectedPlayers.contains(playerId)
            && request.getWeaponName() != null
            && !request.getWeaponName().isEmpty()) {
            sendGameEvent(GameEvent.yellow("Weapon selection is disabled!", playerId));
            request.setWeaponName(null);
        }
        super.handlePlayerConfigChange(playerId, request);
    }
}
