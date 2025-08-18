package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Base;

import java.io.IOException;

public class BaseSerializer extends AbstractSerializer<Base> {
    private final ObstacleSerializer obstacleSerializer = new ObstacleSerializer();

    @Override
    protected void serializeFields(Base base, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // Serialize obstacle fields first (vertices, etc.)
        obstacleSerializer.serializeFields(base, gen, serializers);
        
        // Add base-specific fields
        gen.writeNumberField("x", base.getX());
        gen.writeNumberField("y", base.getY());
        gen.writeNumberField("radius", base.getRadius());
        gen.writeNumberField("hp", base.getHp());
        gen.writeNumberField("maxHp", base.getMaxHp());
        gen.writeNumberField("team", base.getTeam());
        gen.writeBooleanField("destroyed", base.isDestroyed());
        gen.writeNumberField("healthPercentage", base.getHealthPercentage());
    }
}
