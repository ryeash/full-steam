package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.OddballInfo;

import java.io.IOException;

public class OddballInfoSerializer extends AbstractSerializer<OddballInfo> {

    @Override
    public void serializeFields(OddballInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        
        gen.writeFieldName("oddball");
        serializers.defaultSerializeValue(info.getOddball(), gen);
        
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("timeLeft", info.gettimeLeft());
    }
}
