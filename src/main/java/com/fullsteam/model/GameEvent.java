package com.fullsteam.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fullsteam.Config;

/**
 * Represents a short-lived notification for the client to display.
 * e.g., "Team 1 captured the flag!"
 *
 * @param message        The text to display.
 * @param type           A category for the event, allowing the client to style it differently.
 * @param expirationTime The server time (ms) when this event should be removed.
 */
public record GameEvent(String message,
                        EventType type,
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
        EventType type;
        if (team == 1) {
            type = EventType.GREEN;
        } else if (team == 2) {
            type = EventType.RED;
        } else {
            type = EventType.BLUE;
        }
        return new GameEvent(message, type, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent red(String message) {
        return new GameEvent(message, EventType.RED, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent green(String message) {
        return new GameEvent(message, EventType.GREEN, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent yellow(String message) {
        return new GameEvent(message, EventType.YELLOW, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent blue(String message) {
        return new GameEvent(message, EventType.BLUE, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }

    public static GameEvent info(String message) {
        return new GameEvent(message, EventType.GENERIC_INFO, System.currentTimeMillis() + Config.GAME_EVENT_DURATION_MS);
    }
}