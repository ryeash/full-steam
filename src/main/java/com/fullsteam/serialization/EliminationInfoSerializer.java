package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.EliminationInfo;

import java.io.IOException;

public class EliminationInfoSerializer extends AbstractSerializer<EliminationInfo> {
    @Override
    protected void serializeFields(EliminationInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("team1PlayersAlive", info.getTeam1PlayersAlive());
        gen.writeNumberField("team2PlayersAlive", info.getTeam2PlayersAlive());
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
