package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Turret;

import java.io.IOException;

public class TurretSerializer extends JsonSerializer<Turret> {

    @Override
    public void serialize(Turret turret, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("id", turret.id());
        gen.writeNumberField("team", turret.getTeam());
        gen.writeNumberField("x", turret.getX());
        gen.writeNumberField("y", turret.getY());
        gen.writeNumberField("radius", turret.getRadius());
        gen.writeNumberField("angle", turret.getAngle());
        gen.writeNumberField("hp", turret.getHp());
        gen.writeNumberField("maxHp", turret.getMaxHp());
        gen.writeNumberField("currentAmmoInMagazine", turret.getCurrentAmmoInMagazine());
        gen.writeNumberField("reloadCompleteTime", turret.getReloadCompleteTime());
        
        gen.writeEndObject();
    }
}
