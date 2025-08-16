package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.GunMasterInfo;

import java.io.IOException;

public class GunMasterInfoSerializer extends JsonSerializer<GunMasterInfo> {

    @Override
    public void serialize(GunMasterInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("roundTimeRemainingSeconds", info.getRoundTimeRemainingSeconds());
        
        gen.writeEndObject();
    }
}
