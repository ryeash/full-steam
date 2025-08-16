package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.LobbyInfo;

import java.io.IOException;

public class LobbyInfoSerializer extends JsonSerializer<LobbyInfo> {

    @Override
    public void serialize(LobbyInfo lobbyInfo, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("globalPlayerCount", lobbyInfo.globalPlayerCount());
        gen.writeNumberField("maxGlobalPlayers", lobbyInfo.maxGlobalPlayers());
        
        gen.writeFieldName("gameTypes");
        gen.writeStartArray();
        for (String gameType : lobbyInfo.gameTypes()) {
            gen.writeString(gameType);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("activeGames");
        gen.writeStartArray();
        for (var activeGame : lobbyInfo.activeGames()) {
            serializers.defaultSerializeValue(activeGame, gen);
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
