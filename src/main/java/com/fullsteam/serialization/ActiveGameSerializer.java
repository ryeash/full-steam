package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.ActiveGame;

import java.io.IOException;

public class ActiveGameSerializer extends JsonSerializer<ActiveGame> {

    @Override
    public void serialize(ActiveGame activeGame, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("gameId", activeGame.getGameId());
        gen.writeStringField("gameType", activeGame.getGameType());
        gen.writeNumberField("playerCount", activeGame.getPlayerCount());
        gen.writeNumberField("maxPlayers", activeGame.getMaxPlayers());
        
        gen.writeEndObject();
    }
}
