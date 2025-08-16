package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.PowerUp;

import java.io.IOException;

public class PowerUpSerializer extends JsonSerializer<PowerUp> {

    @Override
    public void serialize(PowerUp powerUp, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("id", powerUp.id());
        
        gen.writeFieldName("position");
        serializers.defaultSerializeValue(powerUp.getPosition(), gen);
        
        gen.writeStringField("type", powerUp.getType().name());
        
        gen.writeEndObject();
    }
}
