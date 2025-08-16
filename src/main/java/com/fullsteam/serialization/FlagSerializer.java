package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Flag;

import java.io.IOException;

public class FlagSerializer extends JsonSerializer<Flag> {

    @Override
    public void serialize(Flag flag, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("team", flag.team());
        gen.writeStringField("state", flag.state().name());
        
        gen.writeFieldName("position");
        serializers.defaultSerializeValue(flag.position(), gen);
        
        gen.writeFieldName("basePosition");
        serializers.defaultSerializeValue(flag.basePosition(), gen);
        
        if (flag.carrierId() != null) {
            gen.writeNumberField("carrierId", flag.carrierId());
        }
        
        gen.writeNumberField("dropTimestamp", flag.dropTimestamp());
        
        gen.writeEndObject();
    }
}
