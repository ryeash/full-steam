package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Weapon;

import java.io.IOException;

public class WeaponSerializer extends AbstractSerializer<Weapon> {

    @Override
    public void serializeFields(Weapon weapon, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("name", weapon.getName());
        gen.writeStringField("shortName", weapon.getShortName());
    }
}
