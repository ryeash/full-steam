package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.EliminationInfo;

import java.io.IOException;

public class EliminationInfoSerializer extends JsonSerializer<EliminationInfo> {

    @Override
    public void serialize(EliminationInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("team1PlayersAlive", info.getTeam1PlayersAlive());
        gen.writeNumberField("team2PlayersAlive", info.getTeam2PlayersAlive());
        gen.writeNumberField("roundTimeRemainingSeconds", info.getRoundTimeRemainingSeconds());
        
        gen.writeEndObject();
    }
}
