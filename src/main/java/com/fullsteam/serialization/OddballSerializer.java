package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Oddball;

import java.io.IOException;

public class OddballSerializer extends AbstractSerializer<Oddball> {

    @Override
    public void serializeFields(Oddball oddball, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("state", oddball.state().name());
        
        gen.writeFieldName("position");
        serializers.defaultSerializeValue(oddball.position(), gen);
        
        if (oddball.carrierId() != null) {
            gen.writeNumberField("carrierId", oddball.carrierId());
        }
    }
}
