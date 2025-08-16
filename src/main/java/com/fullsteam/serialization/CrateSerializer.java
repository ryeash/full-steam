package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Crate;

import java.io.IOException;

public class CrateSerializer extends AbstractSerializer<Crate> {
    private final ObstacleSerializer obstacleSerializer = new ObstacleSerializer();

    @Override
    protected void serializeFields(Crate crate, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        obstacleSerializer.serializeFields(crate, gen, serializers);
        gen.writeNumberField("x", crate.getX());
        gen.writeNumberField("y", crate.getY());
        gen.writeNumberField("size", crate.getSize());
        gen.writeNumberField("hp", crate.getHp());
        gen.writeNumberField("maxHp", crate.getMaxHp());
    }
}
