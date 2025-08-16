package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.LoneWolfInfo;

import java.io.IOException;

public class LoneWolfInfoSerializer extends JsonSerializer<LoneWolfInfo> {

    @Override
    public void serialize(LoneWolfInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("loneWolfLives", info.getLoneWolfLives());
        
        gen.writeEndObject();
    }
}
