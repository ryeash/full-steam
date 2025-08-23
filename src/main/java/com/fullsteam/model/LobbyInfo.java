package com.fullsteam.model;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public record LobbyInfo(int globalPlayerCount,
                        int maxGlobalPlayers,
                        List<String> gameTypes,
                        List<ActiveGame> activeGames) {

}
