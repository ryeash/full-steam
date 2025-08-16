package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Oddball;

import java.io.IOException;

public class OddballSerializer extends JsonSerializer<Oddball> {

    @Override
    public void serialize(Oddball oddball, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("state", oddball.state().name());
        
        gen.writeFieldName("position");
        serializers.defaultSerializeValue(oddball.position(), gen);
        
        if (oddball.carrierId() != null) {
            gen.writeNumberField("carrierId", oddball.carrierId());
        }
        
        gen.writeNumberField("dropTimestamp", oddball.dropTimestamp());
        
        gen.writeEndObject();
    }
}
