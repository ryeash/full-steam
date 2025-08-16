package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.FieldEffect;

import java.io.IOException;

import static com.fullsteam.serialization.CustomSerializationModule.withPrecision;

public class FieldEffectSerializer extends AbstractSerializer<FieldEffect> {
    @Override
    protected void serializeFields(FieldEffect fieldEffect, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", fieldEffect.getType().name());
        gen.writeNumberField("x", withPrecision(fieldEffect.getX()));
        gen.writeNumberField("y", withPrecision(fieldEffect.getY()));
        gen.writeNumberField("radius", fieldEffect.getRadius());
        gen.writeNumberField("team", fieldEffect.getTeam());
        gen.writeNumberField("expiration", fieldEffect.getExpiration());
    }
}
