package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.EscortGameInfo;

import java.io.IOException;

public class EscortGameInfoSerializer extends AbstractSerializer<EscortGameInfo> {
    @Override
    protected void serializeFields(EscortGameInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("timeLeft", info.getTimeLeft());

        gen.writeFieldName("obstacle");
        serializers.defaultSerializeValue(info.getObstacle(), gen);

        gen.writeNumberField("captureRadius", info.getCaptureRadius());
    }
}
