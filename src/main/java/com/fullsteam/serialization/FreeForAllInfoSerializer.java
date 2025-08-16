package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.FreeForAllInfo;

import java.io.IOException;

public class FreeForAllInfoSerializer extends JsonSerializer<FreeForAllInfo> {

    @Override
    public void serialize(FreeForAllInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("roundTimeRemainingSeconds", info.getRoundTimeRemainingSeconds());
        
        gen.writeEndObject();
    }
}
