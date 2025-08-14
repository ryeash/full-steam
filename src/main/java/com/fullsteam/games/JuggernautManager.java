package com.fullsteam.games;

import com.fullsteam.Config;
import com.fullsteam.GameLobby;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.ai.IAIStrategy;
import com.fullsteam.model.ai.JuggernautAIStrategy;
import com.fullsteam.model.gamemodes.GameInfo;
import com.fullsteam.model.gamemodes.JuggernautInfo;

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
@GameName("Juggernaut")
public class JuggernautManager extends AbstractTeamBasedManager {

    private Long team1Juggernaut;
    private Long team2Juggernaut;

    public JuggernautManager(GameLobby gameLobby) {
        super(gameLobby);
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
                sendGameEvent(GameEvent.info("Team %s Juggernaut %s was eliminated by %s!".formatted(victim.getTeam(), victim.getPlayerName(), shooter.getPlayerName())));
            } else {
                sendGameEvent(GameEvent.info("Team %s Juggernaut %s was eliminated!".formatted(victim.getTeam(), victim.getPlayerName())));
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
        long roundTimeRemainingSeconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(remainingMillis));
        return new JuggernautInfo(
                (int) this.team1Score,
                (int) this.team2Score,
                team1Juggernaut,
                team2Juggernaut,
                roundTimeRemainingSeconds
        );
    }

    private void triggerJuggernautReset() {
        // Select new Juggernauts
        team1Juggernaut = null;
        team2Juggernaut = null;
        for (Player player : players.values()) {
            player.setMaxHp(Config.DEFAULT_PLAYER_HEALTH);
            player.setHp(Config.DEFAULT_PLAYER_HEALTH);
        }
        sendGameEvent(GameEvent.info("Starting new round!"));
        sendGameEvent(GameEvent.blue("Will select new juggernauts in " + (JUGGERNAUT_SELECTION_DELAY_MS / 1000) + " seconds"));
        players.values().forEach(this::setValidSpawnPosition);
        schedule(() -> {
            selectNewJuggernautForTeam(1);
            selectNewJuggernautForTeam(2);
        }, JUGGERNAUT_SELECTION_DELAY_MS);
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
            juggernaut.setHp(JUGGERNAUT_HEALTH);
            juggernaut.setMaxHp(JUGGERNAUT_HEALTH);
            sendGameEvent(GameEvent.team(team, "%s is Team %d's Juggernaut!".formatted(juggernaut.getPlayerName(), team)));
            log.info("{} is the new Juggernaut for team {}", juggernaut.getPlayerName(), team);
        } else {
            log.warn("Cannot select Juggernaut for team {}: no players on team.", team);
        }
    }
}