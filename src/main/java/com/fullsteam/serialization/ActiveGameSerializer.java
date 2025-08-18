package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.ActiveGame;

import java.io.IOException;

public class ActiveGameSerializer extends AbstractSerializer<ActiveGame> {
    @Override
    protected void serializeFields(ActiveGame activeGame, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumberField("gameId", activeGame.getGameId());
        gen.writeStringField("gameType", activeGame.getGameType());
        gen.writeNumberField("playerCount", activeGame.getPlayerCount());
        gen.writeNumberField("maxPlayers", activeGame.getMaxPlayers());
    }
}
