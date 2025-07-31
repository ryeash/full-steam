package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.ai.AIArchetype;
import com.fullsteam.model.ai.AIPlayer;
import com.fullsteam.model.ai.JuggernautAIStrategy;
import com.fullsteam.model.gamemodes.JuggernautInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * A round-based game mode where each team has one "Juggernaut".
 * A team scores by eliminating the enemy Juggernaut.
 */
public class JuggernautManager extends AbstractTeamBasedManager {

    private static final int SCORE_TO_WIN = 3;
    private static final int JUGGERNAUT_HEALTH = 1000;
    private static final long WAIT_FOR_PLAYER_JOIN = 7000;
    private String team1Juggernaut;
    private String team2Juggernaut;

    public JuggernautManager(GameLobby gameLobby) {
        super(gameLobby);
    }

    @Override
    public String gameType() {
        return "Juggernaut";
    }

    @Override
    public void addAIPlayer(int team) {
        String playerId = "ai-" + UUID.randomUUID();
        AIPlayer player = new AIPlayer(playerId, 0, 0, team, new JuggernautAIStrategy(), AIArchetype.randomArchetype());
        setValidSpawnPosition(player);
        players.put(playerId, player);
        log.info("AI Player {} (Juggernaut Strategy) joined team {}", playerId, team);
    }

    @Override
    public void startGameLoop() {
        triggerJuggernautReset();
        super.startGameLoop();
    }

    @Override
    protected void updatePlayers() {
        if (team1Juggernaut == null || team2Juggernaut == null) {
            // freeze until the juggernauts are selected
            return;
        }
        super.updatePlayers();
    }

    @Override
    public void handlePlayerInput(String playerId, PlayerInput input) {
        if (team1Juggernaut == null || team2Juggernaut == null) {
            // freeze until the juggernauts are selected
            return;
        }
        super.handlePlayerInput(playerId, input);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (Objects.equals(victim.getId(), team1Juggernaut)
            || Objects.equals(victim.getId(), team2Juggernaut)) {
            sendGameEvent(GameEvent.info("Team %s Juggernaut %s was eliminated by %s!".formatted(victim.getTeam(), victim.getPlayerName(), shooter.getPlayerName())));
            team1Juggernaut = null;
            team2Juggernaut = null;
            if (shooter.getTeam() == 1) {
                team1Score++;
            } else {
                team2Score++;
            }
            triggerJuggernautReset();
        }
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() > roundEndTime
            || team1Score >= SCORE_TO_WIN
            || team2Score >= SCORE_TO_WIN) {
            sendVictoryMessage();
            return true;
        }
        return false;
    }

    @Override
    protected GameState buildGameState() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));

        JuggernautInfo gameInfo = new JuggernautInfo(
                (int) this.team1Score,
                (int) this.team2Score,
                team1Juggernaut,
                team2Juggernaut,
                roundTimeRemainingSeconds
        );

        return new GameState(
                List.copyOf(players.values()),
                List.copyOf(bullets),
                List.copyOf(obstacles),
                List.copyOf(deathMarkers),
                List.copyOf(gameEvents),
                gameInfo
        );
    }

    private void triggerJuggernautReset() {
        // Select new Juggernauts
        team1Juggernaut = null;
        team2Juggernaut = null;
        for (Player player : players.values()) {
            player.setMaxHealth(Config.DEFAULT_PLAYER_HEALTH);
            player.setCurrentHealth(Config.DEFAULT_PLAYER_HEALTH);
        }
        sendGameEvent(GameEvent.info("Starting new round!"));
        sendGameEvent(GameEvent.info("Will select new juggernauts in " + (WAIT_FOR_PLAYER_JOIN / 1000) + " seconds"));
        players.values().forEach(this::setValidSpawnPosition);
        gameLoop.schedule(() -> {
            selectNewJuggernautForTeam(1);
            selectNewJuggernautForTeam(2);
        }, WAIT_FOR_PLAYER_JOIN, TimeUnit.MILLISECONDS);
    }

    private void selectNewJuggernautForTeam(int team) {
        List<Player> teamPlayers = players.values().stream()
                .filter(p -> p.getTeam() == team)
                .toList();

        if (!teamPlayers.isEmpty()) {
            Player juggernaut = teamPlayers.get(ThreadLocalRandom.current().nextInt(teamPlayers.size()));
            if (team == 1) {
                team1Juggernaut = juggernaut.getId();
            } else {
                team2Juggernaut = juggernaut.getId();
            }
            juggernaut.setCurrentHealth(JUGGERNAUT_HEALTH);
            juggernaut.setMaxHealth(JUGGERNAUT_HEALTH);
            sendGameEvent(GameEvent.info("%s is Team %d's Juggernaut!".formatted(juggernaut.getPlayerName(), team)));
            log.info("{} is the new Juggernaut for team {}", juggernaut.getPlayerName(), team);
        } else {
            log.warn("Cannot select Juggernaut for team {}: no players on team.", team);
        }
    }
}