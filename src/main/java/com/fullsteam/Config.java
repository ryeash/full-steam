package com.fullsteam;

import java.util.Optional;
import java.util.function.Function;

public class Config {

    // Helper methods to read from System Properties with defaults
    private static boolean getBool(String name, boolean defaultValue) {
        return getProp(name, defaultValue, Boolean::parseBoolean);
    }

    private static int getInt(String name, int defaultValue) {
        return getProp(name, defaultValue, Integer::parseInt);
    }

    private static long getLong(String name, long defaultValue) {
        return getProp(name, defaultValue, Long::parseLong);
    }

    private static double getDouble(String name, double defaultValue) {
        return getProp(name, defaultValue, Double::parseDouble);
    }

    private static <T> T getProp(String name, T fallback, Function<String, T> mapper) {
        return Optional.ofNullable(System.getProperty(name))
                .or(() -> Optional.ofNullable(System.getenv(name)))
                .map(mapper)
                .orElse(fallback);
    }

    // --- Server Configuration ---
    public static final int PORT;

    // --- Base AI Configuration ---
    public static final double VISION_RANGE;
    public static final long WANDER_DIRECTION_CHANGE_INTERVAL; // ms

    // Base values for generating AI personalities
    public static final long BASE_REACTION_TIME_MS;
    public static final double BASE_AIM_INACCURACY_RADIANS;
    public static final long BASE_STRAFE_INTERVAL_MS;
    public static final double BASE_STRAFE_CHANCE;

    // --- GameLobby ---
    public static final long CLEANUP_INTERVAL_SECONDS;
    public static final int MAX_GLOBAL_PLAYERS;

    // --- GameStateManager ---
    public static final long AFK_TIMEOUT_MS;
    public static final int GAME_WIDTH;
    public static final int GAME_HEIGHT;
    public static final int TICK_RATE;
    public static final int OBSTACLE_COUNT;
    public static final long DEATH_MARKER_DURATION_MS = 5000; // Marker lasts 5 seconds
    public static final long GAME_EVENT_DURATION_MS = 4000; // 4 seconds for events to be on screen
    public static final long NEXT_ROUND_DELAY_MS = 3000; // 3-second delay between rounds
    public static final long RESPAWN_DELAY_MS;
    public static final double PLAYER_SIZE;
    public static final double DEFAULT_PLAYER_HEALTH = 100.0;
    public static final double DEFAULT_PLAYER_SPEED = 3.0;
    public static final double ZOMBIE_SPEED = 1.9; // Slower than players
    public static final long ROUND_DURATION_SECONDS;
    public static final double SPAWN_HORIZONTAL_PADDING;
    public static final double SPAWN_VERTICAL_PADDING;
    public static final double SPAWN_MIDFIELD_BUFFER;
    public static final int MAX_PLAYERS_PER_TEAM;
    public static final boolean ROTATE_GAME_MODES;

    static {
        PORT = getInt("PORT", 8080);

        // AI
        VISION_RANGE = getDouble("ai.vision_range", 450.0);
        WANDER_DIRECTION_CHANGE_INTERVAL = getLong("ai.wander_interval_ms", 2000);
        BASE_REACTION_TIME_MS = getLong("ai.base_reaction_ms", 500);
        BASE_AIM_INACCURACY_RADIANS = getDouble("ai.base_aim_inaccuracy", 0.05);
        BASE_STRAFE_INTERVAL_MS = getLong("ai.base_strafe_interval_ms", 1500);
        BASE_STRAFE_CHANCE = getDouble("ai.base_strafe_chance", 0.4);

        // Lobby
        CLEANUP_INTERVAL_SECONDS = getLong("lobby.cleanup_interval_s", 10);
        MAX_GLOBAL_PLAYERS = getInt("lobby.max_global_players", 50);

        // Game
        AFK_TIMEOUT_MS = getLong("game.afk_timeout_ms", 30_000);
        GAME_WIDTH = getInt("game.width", 1000);
        GAME_HEIGHT = getInt("game.height", 800);
        TICK_RATE = getInt("game.tick_rate", 60);
        OBSTACLE_COUNT = getInt("game.obstacle_count", 9);
        RESPAWN_DELAY_MS = getLong("game.respawn_delay_ms", 3_000);
        PLAYER_SIZE = getDouble("game.player_size", 20.0);
        ROUND_DURATION_SECONDS = getLong("game.round_duration_s", 180);
        SPAWN_HORIZONTAL_PADDING = getDouble("game.spawn_padding_h", 50.0);
        SPAWN_VERTICAL_PADDING = getDouble("game.spawn_padding_v", 50.0);
        SPAWN_MIDFIELD_BUFFER = getDouble("game.spawn_midfield_buffer", 100.0);
        MAX_PLAYERS_PER_TEAM = getInt("game.max_players_per_team", 5);
        ROTATE_GAME_MODES = getBool("game.rotate_game_modes", true);
    }
}
