package com.fullsteam;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class Config {

    // Helper methods to read from System Properties with defaults
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

    // global scheduler
    public static final ScheduledExecutorService EXECUTOR = Executors.newScheduledThreadPool(8);

    // global ID assignment
    public static final AtomicLong ID_COUNTER = new AtomicLong();

    // --- Server Configuration ---
    public static final int PORT = getInt("server.port", 8080);

    // AI
    public static final double VISION_RANGE = getDouble("ai.vision_range", 450.0);
    public static final long WANDER_DIRECTION_CHANGE_INTERVAL = getLong("ai.wander_interval_ms", 2000); // ms
    public static final long BASE_REACTION_TIME_MS = getLong("ai.base_reaction_ms", 600);
    public static final double BASE_AIM_INACCURACY_RADIANS = getDouble("ai.base_aim_inaccuracy", 0.2);
    public static final long BASE_STRAFE_INTERVAL_MS = getLong("ai.base_strafe_interval_ms", 1500);
    public static final double AI_MAX_FORCE = getDouble("ai.max_force", 0.2); // The maximum steering force, controls turning ability

    // Lobby
    public static final long CLEANUP_INTERVAL_SECONDS = getLong("lobby.cleanup_interval_s", 10);
    public static final int MAX_GLOBAL_PLAYERS = getInt("lobby.max_global_players", 50);

    // Game
    public static final int GAME_WIDTH = getInt("game.width", 1000);
    public static final int GAME_HEIGHT = getInt("game.height", 800);
    public static final int TICK_RATE = getInt("game.tick_rate", 60);
    public static final boolean ALLOW_NAME_CHANGE = getProp("game.allow_name_change", true, Boolean::valueOf);
    public static final int OBSTACLE_COUNT = getInt("game.obstacle_count", 8);
    public static final long DEATH_MARKER_DURATION_MS = getLong("game.death_marker_duration_ms", 5000); // Marker lasts 5 seconds
    public static final long GAME_EVENT_DURATION_MS = getLong("game.game_event_duration_ms", 6000); // 4 seconds for events to be on screen
    public static final long NEXT_ROUND_DELAY_MS = getLong("game.next_round_delay_ms", 6000); // 3-second delay between rounds
    public static final long RESPAWN_DELAY_MS = getLong("game.respawn_delay_ms", 5_000);
    public static final double PLAYER_SIZE = getDouble("game.player_size", 20.0);
    public static final double DEFAULT_PLAYER_HEALTH = getDouble("game.default_player_health", 100.0);
    public static final double DEFAULT_PLAYER_SPEED = getDouble("game.default_player_speed", 3.0);
    public static final double ZOMBIE_SPEED = getDouble("game.zombie_speed", 1.2); // Slower than players
    public static final long ROUND_DURATION_SECONDS = getLong("game.round_duration_s", 180);
    public static final long AFK_TIMEOUT_MS = getLong("game.afk_timeout_ms", (ROUND_DURATION_SECONDS * 1000) + 10_000L);
    public static final double SPAWN_HORIZONTAL_PADDING = getDouble("game.spawn_padding_h", 50.0);
    public static final double SPAWN_VERTICAL_PADDING = getDouble("game.spawn_padding_v", 50.0);
    public static final double SPAWN_MIDFIELD_BUFFER = getDouble("game.spawn_midfield_buffer", 100.0);
    public static final int MAX_PLAYERS_PER_TEAM = getInt("game.max_players_per_team", 5);
    public static final int HAZARD_COUNT = getInt("game.hazard.count", 2);
    public static final double HAZARD_SLOW_FACTOR = getDouble("game.hazard.slow_factor", 0.5); // 50% speed
    public static final double HAZARD_DAMAGE_FACTOR = getDouble("game.hazard.damage_factor", 0.5); // damage per tick;
    public static final long RESPAWN_IMMUNITY_DURATION = getLong("game.respawn_immunity_duration", 2000);

    // --- Power Ups ---
    public static final double POWER_UP_SPEED_BOOST_FACTOR = getDouble("game.powerup.speed_boost_factor", 1.5);
    public static final double POWER_UP_DAMAGE_BOOST_MULTIPLIER = getDouble("game.powerup.damage_boost_multiplier", 1.5);
    public static final long POWER_UP_HEALTH_RECOVERY = getLong("game.powerup.health_recovery", 50);
    public static final long POWER_UP_SPEED_BOOST_DURATION = getLong("game.powerup.speed_boost_duration", 5000);
    public static final long POWER_UP_ARMOR_UP_DURATION = getLong("game.powerup.armor_up_duration", 3000);
    public static final long POWER_UP_DAMAGE_BOOST_DURATION = getLong("game.powerup.damage_boost_duration", 5000);

    // --- Capture the Flag (CTF) Game Mode ---
    public static final int CTF_SCORE_TO_WIN = getInt("game.ctf.score_to_win", 3);
    public static final double CTF_FLAG_PICKUP_RADIUS = getDouble("game.ctf.flag_pickup_radius", 30.0);
    public static final long CTF_FLAG_RETURN_TIMEOUT_MS = getLong("game.ctf.flag_return_timeout_ms", 15_000);
    public static final double CTF_BASE_AREA_PADDING = getDouble("game.ctf.base_area_padding", 100.0);

    // --- Elimination Game Mode ---
    public static final int ELIMINATION_SCORE_TO_WIN = getInt("game.elimination.score_to_win", 5);

    // --- Juggernaut Game Mode ---
    public static final int JUGGERNAUT_SCORE_TO_WIN = getInt("game.juggernaut.score_to_win", 3);
    public static final int JUGGERNAUT_HEALTH = getInt("game.juggernaut.health", 1000);
    public static final long JUGGERNAUT_SELECTION_DELAY_MS = getLong("game.juggernaut.selection_delay_ms", 7000);

    // --- King of the Hill (KOTH) Game Mode ---
    public static final double KOTH_SCORE_TO_WIN = getDouble("game.koth.score_to_win", 100.0);
    public static final double KOTH_POINTS_PER_SECOND = getDouble("game.koth.points_per_second", 1.0);
    public static final double KOTH_HILL_RADIUS = getDouble("game.koth.hill_radius", 75.0);
    public static final double KOTH_HILL_KEEP_OUT_RADIUS = getDouble("game.koth.hill_keep_out_radius", 125.0);

    // --- Oddball Game Mode ---
    public static final double ODDBALL_SCORE_TO_WIN = getDouble("game.oddball.score_to_win", (double) ROUND_DURATION_SECONDS / 2);
    public static final double ODDBALL_POINTS_PER_SECOND = getDouble("game.oddball.points_per_second", 1.0);
    public static final double ODDBALL_BALL_PICKUP_RADIUS = getDouble("game.oddball.ball_pickup_radius", 30.0);
    public static final long ODDBALL_BALL_RESET_TIMEOUT_MS = getLong("game.oddball.ball_reset_timeout_ms", 15_000);
    public static final double ODDBALL_KEEP_OUT_RADIUS = getDouble("game.oddball.keep_out_radius", 150.0);

    // --- Zombie Defense Game Mode ---
    public static final long ZOMBIE_TIME_BETWEEN_WAVES_MS = getLong("game.zombie.time_between_waves_ms", 15_000);
    public static final long ZOMBIE_INITIAL_WAVE_DELAY_MS = getLong("game.zombie.initial_wave_delay_ms", 5_000);

    // --- Escort Game Mode ---
    public static final double ESCORT_OBSTACLE_WIDTH = getDouble("game.escort.obstacle_width", 50.0);
    public static final double ESCORT_OBSTACLE_HEIGHT = getDouble("game.escort.obstacle_height", 40.0);
    public static final double ESCORT_OBSTACLE_SPEED = getDouble("game.escort.obstacle_speed", 1.0);
    public static final double ESCORT_PLAYER_PROXIMITY = getDouble("game.escort.player_proximity", 100.0);

    // --- Lone Wolf Game Mode ---
    public static final double LONE_WOLF_HEALTH_MULTIPLIER = getDouble("game.lonewolf.health_multiplier", 5.0);
    public static final int LONE_WOLF_LIVES = getInt("game.lonewolf.lives", 3);
    public static final double LONE_WOLF_DAMAGE_BOOST_PER_DEATH = getDouble("game.lonewolf.damage_boost_per_death", 0.50);

    // --- Builder Game Mode ---
    public static final int BUILDER_MAX_OBSTACLES = getInt("game.builder.max_obstacles", 50);
    public static final int BUILDER_CRATE_HEALTH = getInt("game.builder.crate_health", 1000);
}
