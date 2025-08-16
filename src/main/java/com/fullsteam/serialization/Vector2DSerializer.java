package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Vector2D;

import java.io.IOException;

import static com.fullsteam.serialization.CustomSerializationModule.withPrecision;

public class Vector2DSerializer extends JsonSerializer<Vector2D> {

    @Override
    public void serialize(Vector2D vector, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("x", withPrecision(vector.x()));
        gen.writeNumberField("y", withPrecision(vector.y()));
        gen.writeEndObject();
    }
}
