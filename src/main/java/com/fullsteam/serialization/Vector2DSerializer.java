package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Vector2D;

import java.io.IOException;

import static com.fullsteam.serialization.CustomSerializationModule.withPrecision;

public class Vector2DSerializer extends AbstractSerializer<Vector2D> {

    @Override
    public void serializeFields(Vector2D vector, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumberField("x", withPrecision(vector.x()));
        gen.writeNumberField("y", withPrecision(vector.y()));
    }
}
