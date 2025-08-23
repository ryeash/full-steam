package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;

import java.io.IOException;

public class CaptureTheFlagInfoSerializer extends AbstractSerializer<CaptureTheFlagInfo> {
    @Override
    protected void serializeFields(CaptureTheFlagInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());

        gen.writeFieldName("team1Flag");
        serializers.defaultSerializeValue(info.getTeam1Flag(), gen);

        gen.writeFieldName("team2Flag");
        serializers.defaultSerializeValue(info.getTeam2Flag(), gen);

        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
