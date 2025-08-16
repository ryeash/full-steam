package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Bullet;

import java.io.IOException;

public class BulletSerializer extends AbstractSerializer<Bullet> {
    @Override
    protected void serializeFields(Bullet value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumberField("x", value.getX());
        gen.writeNumberField("y", value.getY());
        gen.writeNumberField("team", value.getTeam());
    }
}
