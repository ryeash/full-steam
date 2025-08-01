package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fullsteam.Config;

/**
 * Represents a short-lived notification for the client to display.
 * e.g., "Team 1 captured the flag!"
 *
 * @param message        The text to display.
 * @param type           A category for the event, allowing the client to style it differently.
 * @param playerId       The player to send this event to. If null, it's broadcast to all players.
 * @param expirationTime The server time (ms) when this event should be removed.
 */
public record GameEvent(String message,
                        EventType type,
                        String playerId,
                        @JsonIgnore long expirationTime) {
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

    public static GameEvent team(int team, String message, String playerId) {
        EventType type;
        if (team == 1) {
            type = EventType.GREEN;
        } else if (team == 2) {
            type = EventType.RED;
        } else {
            type = EventType.BLUE;
        }
        return new GameEvent(message, type, playerId, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent red(String message) {
        return red(message, null);
    }

    public static GameEvent red(String message, String playerId) {
        return new GameEvent(message, EventType.RED, playerId, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent green(String message) {
        return green(message, null);
    }

    public static GameEvent green(String message, String playerId) {
        return new GameEvent(message, EventType.GREEN, playerId, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent yellow(String message) {
        return yellow(message, null);
    }

    public static GameEvent yellow(String message, String playerId) {
        return new GameEvent(message, EventType.YELLOW, playerId, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent blue(String message) {
        return blue(message, null);
    }

    public static GameEvent blue(String message, String playerId) {
        return new GameEvent(message, EventType.BLUE, playerId, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent info(String message) {
        return info(message, null);
    }

    public static GameEvent info(String message, String playerId) {
        return new GameEvent(message, EventType.GENERIC_INFO, playerId, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }
}
