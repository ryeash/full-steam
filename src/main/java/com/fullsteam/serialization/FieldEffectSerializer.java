package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.FieldEffect;

import java.io.IOException;

import static com.fullsteam.serialization.CustomSerializationModule.withPrecision;

public class FieldEffectSerializer extends JsonSerializer<FieldEffect> {

    @Override
    public void serialize(FieldEffect fieldEffect, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        gen.writeNumberField("id", fieldEffect.id());
        gen.writeStringField("type", fieldEffect.getType().name());
        gen.writeNumberField("x", withPrecision(fieldEffect.getX()));
        gen.writeNumberField("y", withPrecision(fieldEffect.getY()));
        gen.writeNumberField("radius", fieldEffect.getRadius());
        gen.writeNumberField("team", fieldEffect.getTeam());
        gen.writeNumberField("expiration", fieldEffect.getExpiration());

        gen.writeEndObject();
    }
}
