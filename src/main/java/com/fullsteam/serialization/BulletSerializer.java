package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Bullet;

import java.io.IOException;

public class BulletSerializer extends JsonSerializer<Bullet> {

    @Override
    public void serialize(Bullet bullet, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField("id", bullet.id());
        gen.writeNumberField("x", bullet.getX());
        gen.writeNumberField("y", bullet.getY());
        gen.writeNumberField("team", bullet.getTeam());
        gen.writeEndObject();
    }
}
