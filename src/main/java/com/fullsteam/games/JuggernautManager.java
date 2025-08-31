package com.fullsteam.games;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PlayerSession;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.ai.JuggernautAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.JuggernautInfo;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.websocket.WebSocketSession;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fullsteam.Config.JUGGERNAUT_HEALTH;
import static com.fullsteam.Config.JUGGERNAUT_SCORE_TO_WIN;
import static com.fullsteam.Config.JUGGERNAUT_SELECTION_DELAY_MS;

/**
 * A round-based game mode where each team has one "Juggernaut".
 * A team scores by eliminating the enemy Juggernaut.
 */
@Prototype
public class JuggernautManager extends AbstractTeamBasedManager {

    private Long team1Juggernaut;
    private Long team2Juggernaut;

    public JuggernautManager(ObjectMapper objectMapper, GameLobby gameLobby) {
        super(objectMapper, gameLobby);
    }

    @Override
    public PlayerSession addPlayer(long playerId, WebSocketSession channel) {
        PlayerSession playerSession = super.addPlayer(playerId, channel);
        sendAwaitingJuggernautMessage(playerSession);
        return playerSession;
    }

    @Override
    protected PlayerSession addPlayer(long playerId, WebSocketSession channel, int team) {
        PlayerSession playerSession = super.addPlayer(playerId, channel, team);
        sendAwaitingJuggernautMessage(playerSession);
        return playerSession;
    }

    private void sendAwaitingJuggernautMessage(PlayerSession playerSession) {
        if (team1Juggernaut == null || team2Juggernaut == null) {
            sendGameEvent(GameEvent.info("Waiting for juggernaut promotion", playerSession.getPlayerId()));
        }
    }

    @Override
    protected IAIStrategy buildAIStrategy() {
        return new JuggernautAIStrategy();
    }

    @Override
    public void startGameLoop() {
        triggerJuggernautReset();
        super.startGameLoop();
    }

    @Override
    protected void updatePlayers(long delta) {
        if (team1Juggernaut == null || team2Juggernaut == null) {
            // freeze until the juggernauts are selected
            return;
        }
        super.updatePlayers(delta);
    }

    @Override
    public void acceptPlayerInput(Long playerId, PlayerInput input) {
        if (team1Juggernaut == null || team2Juggernaut == null) {
            // freeze until the juggernauts are selected
            return;
        }
        super.acceptPlayerInput(playerId, input);
    }

    @Override
    protected void killPlayer(Player victim, Player shooter) {
        super.killPlayer(victim, shooter);
        if (Objects.equals(victim.getId(), team1Juggernaut) || Objects.equals(victim.getId(), team2Juggernaut)) {
            if (shooter != null) {
                sendGameEvent(GameEvent.info("Team %s Juggernaut %s was eliminated by %s!".formatted(victim.getTeam(), victim.getName(), shooter.getName())));
            } else {
                sendGameEvent(GameEvent.info("Team %s Juggernaut %s was eliminated!".formatted(victim.getTeam(), victim.getName())));
            }
            team1Juggernaut = null;
            team2Juggernaut = null;
            if (victim.getTeam() == 1) {
                team2Score++;
            } else {
                team1Score++;
            }
            triggerJuggernautReset();
        }
    }

    @Override
    protected boolean checkEndConditions() {
        if (System.currentTimeMillis() > roundEndTime
            || team1Score >= JUGGERNAUT_SCORE_TO_WIN
            || team2Score >= JUGGERNAUT_SCORE_TO_WIN) {
            sendVictoryMessage();
            return true;
        }
        return false;
    }

    @Override
    protected GameInfo buildGameInfo() {
        long remainingMillis = roundEndTime - System.currentTimeMillis();
        long timeLeft = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new JuggernautInfo(
                (int) this.team1Score,
                (int) this.team2Score,
                team1Juggernaut,
                team2Juggernaut,
                timeLeft
        );
    }

    private void triggerJuggernautReset() {
        // Select new Juggernauts
        team1Juggernaut = null;
        team2Juggernaut = null;
        for (Player player : entities.getPlayers()) {
            player.setMaxHp(Config.DEFAULT_PLAYER_HEALTH);
            player.setHp(Config.DEFAULT_PLAYER_HEALTH);
        }
        sendGameEvent(GameEvent.info("Starting new round!"));
        sendGameEvent(GameEvent.blue("Will select new juggernauts in " + (JUGGERNAUT_SELECTION_DELAY_MS / 1000) + " seconds"));
        entities.getPlayers().forEach(this::setValidSpawnPosition);
        schedule(() -> {
            selectNewJuggernautForTeam(1);
            selectNewJuggernautForTeam(2);
        }, JUGGERNAUT_SELECTION_DELAY_MS);
    }

    private void selectNewJuggernautForTeam(int team) {
        List<Player> teamPlayers = entities.getPlayers().stream()
                .filter(p -> p.getTeam() == team)
                .toList();

        if (!teamPlayers.isEmpty()) {
            Player juggernaut = teamPlayers.get(ThreadLocalRandom.current().nextInt(teamPlayers.size()));
            if (team == 1) {
                team1Juggernaut = juggernaut.getId();
            } else {
                team2Juggernaut = juggernaut.getId();
            }
            juggernaut.setHp(JUGGERNAUT_HEALTH);
            juggernaut.setMaxHp(JUGGERNAUT_HEALTH);
            sendGameEvent(GameEvent.team(team, "%s is Team %d's Juggernaut!".formatted(juggernaut.getName(), team)));
            log.info("{} is the new Juggernaut for team {}", juggernaut.getName(), team);
        } else {
            log.warn("Cannot select Juggernaut for team {}: no players on team.", team);
        }
    }
}