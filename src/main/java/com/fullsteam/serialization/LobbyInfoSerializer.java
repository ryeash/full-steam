package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.ActiveGame;
import com.fullsteam.model.LobbyInfo;

import java.io.IOException;

public class LobbyInfoSerializer extends AbstractSerializer<LobbyInfo> {

    @Override
    public void serializeFields(LobbyInfo lobbyInfo, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumberField("globalPlayerCount", lobbyInfo.globalPlayerCount());
        gen.writeNumberField("maxGlobalPlayers", lobbyInfo.maxGlobalPlayers());
        
        gen.writeArrayFieldStart("gameTypes");
        for (String gameType : lobbyInfo.gameTypes()) {
            gen.writeString(gameType);
        }
        gen.writeEndArray();
        
        gen.writeArrayFieldStart("activeGames");
        for (ActiveGame activeGame : lobbyInfo.activeGames()) {
            serializers.defaultSerializeValue(activeGame, gen);
        }
        gen.writeEndArray();
    }
}
