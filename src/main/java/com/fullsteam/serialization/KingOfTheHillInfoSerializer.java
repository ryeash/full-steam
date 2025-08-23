package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;

import java.io.IOException;

public class KingOfTheHillInfoSerializer extends AbstractSerializer<KingOfTheHillInfo> {

    @Override
    public void serializeFields(KingOfTheHillInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        
        gen.writeFieldName("hill");
        serializers.defaultSerializeValue(info.getHill(), gen);
        
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
