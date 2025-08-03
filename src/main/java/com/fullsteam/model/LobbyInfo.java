package com.fullsteam.model;

import java.util.List;

public record LobbyInfo(int globalPlayerCount,
                        int maxGlobalPlayers,
                        List<String> gameTypes,
                        List<ActiveGame> activeGames) {

}
