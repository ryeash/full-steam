package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Weapon;

import java.io.IOException;

public class WeaponSerializer extends JsonSerializer<Weapon> {

    @Override
    public void serialize(Weapon weapon, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("name", weapon.getName());
        gen.writeStringField("shortName", weapon.getShortName());
        gen.writeEndObject();
    }
}
