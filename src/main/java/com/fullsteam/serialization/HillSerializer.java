package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Hill;

import java.io.IOException;

public class HillSerializer extends JsonSerializer<Hill> {

    @Override
    public void serialize(Hill hill, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeFieldName("position");
        serializers.defaultSerializeValue(hill.position(), gen);
        
        gen.writeNumberField("radius", hill.radius());
        gen.writeNumberField("controllingTeam", hill.controllingTeam());
        gen.writeBooleanField("contested", hill.contested());
        
        gen.writeEndObject();
    }
}
