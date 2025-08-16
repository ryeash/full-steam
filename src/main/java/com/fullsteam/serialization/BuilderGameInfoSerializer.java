package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.BuilderGameInfo;

import java.io.IOException;

public class BuilderGameInfoSerializer extends JsonSerializer<BuilderGameInfo> {

    @Override
    public void serialize(BuilderGameInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", info.getType());
        
        gen.writeFieldName("crates");
        gen.writeStartArray();
        for (var crate : info.getCrates()) {
            serializers.defaultSerializeValue(crate, gen);
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
