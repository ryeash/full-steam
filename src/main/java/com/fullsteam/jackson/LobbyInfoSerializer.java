package com.fullsteam.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fullsteam.model.ActiveGame;
import com.fullsteam.model.LobbyInfo;

import java.io.IOException;

public class LobbyInfoSerializer extends StdSerializer<LobbyInfo> {
    public LobbyInfoSerializer() {
        super(LobbyInfo.class);
    }

    @Override
    public void serialize(LobbyInfo lobbyInfo, JsonGenerator jgen, SerializerProvider serializerProvider) throws IOException {
        jgen.writeStartObject();
        jgen.writeNumberField("globalPlayerCount", lobbyInfo.globalPlayerCount());
        jgen.writeNumberField("maxGlobalPlayers", lobbyInfo.maxGlobalPlayers());
        jgen.writeArrayFieldStart("gameTypes");
        for (String gameType : lobbyInfo.gameTypes()) {
            jgen.writeString(gameType);
        }
        jgen.writeEndArray();
        jgen.writeArrayFieldStart("activeGames");
        for (ActiveGame activeGame : lobbyInfo.activeGames()) {
            writeActiveGame(jgen, activeGame);
        }
        jgen.writeEndArray();
        jgen.writeEndObject();
    }

    private void writeActiveGame(JsonGenerator jgen, ActiveGame activeGame) throws IOException {
        jgen.writeStartObject();
        jgen.writeNumberField("gameId", activeGame.getGameId());
        jgen.writeStringField("gameType", activeGame.getGameType());
        jgen.writeNumberField("playerCount", activeGame.getPlayerCount());
        jgen.writeNumberField("maxPlayers", activeGame.getMaxPlayers());
        jgen.writeEndObject();
    }
}
