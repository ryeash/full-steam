package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fullsteam.Config;

/**
 * Represents a short-lived notification for the client to display.
 * e.g., "Team 1 captured the flag!"
 *
 * @param message  The text to display.
 * @param type     A category for the event, allowing the client to style it differently.
 * @param playerId The player to send this event to. If null, it's broadcast to all players.
 */
public record GameEvent(String message,
                        EventType type,
                        long expirationTime,
                        @JsonInclude(Include.NON_NULL) Long playerId) {
    public enum EventType {
        FLAG_PICKUP,
        FLAG_DROP,
        FLAG_RETURN,
        FLAG_CAPTURE,
        RED,
        GREEN,
        YELLOW,
        BLUE,
        GENERIC_INFO
    }

    public static GameEvent team(int team, String message) {
        return team(team, message, null);
    }

    public static GameEvent team(int team, String message, Long playerId) {
        EventType type;
        if (team == 1) {
            type = EventType.GREEN;
        } else if (team == 2) {
            type = EventType.RED;
        } else {
            type = EventType.BLUE;
        }
        return new GameEvent(message, type, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, playerId);
    }

    public static GameEvent red(String message) {
        return red(message, null);
    }

    public static GameEvent red(String message, Long playerId) {
        return new GameEvent(message, EventType.RED, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, playerId);
    }

    public static GameEvent green(String message) {
        return green(message, null);
    }

    public static GameEvent green(String message, Long playerId) {
        return new GameEvent(message, EventType.GREEN, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, playerId);
    }

    public static GameEvent yellow(String message) {
        return yellow(message, null);
    }

    public static GameEvent yellow(String message, Long playerId) {
        return new GameEvent(message, EventType.YELLOW, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, playerId);
    }

    public static GameEvent blue(String message) {
        return blue(message, null);
    }

    public static GameEvent blue(String message, Long playerId) {
        return new GameEvent(message, EventType.BLUE, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, playerId);
    }

    public static GameEvent info(String message) {
        return info(message, null);
    }

    public static GameEvent info(String message, Long playerId) {
        return new GameEvent(message, EventType.GENERIC_INFO, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, playerId);
    }

    public static GameEvent withType(String message, EventType type) {
        return new GameEvent(message, type, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS, null);
    }
}
