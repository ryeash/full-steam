package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.PowerUp;

import java.io.IOException;

public class PowerUpSerializer extends AbstractSerializer<PowerUp> {

    @Override
    public void serializeFields(PowerUp powerUp, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeFieldName("position");
        serializers.defaultSerializeValue(powerUp.getPosition(), gen);
        gen.writeStringField("type", powerUp.getType().name());
    }
}
