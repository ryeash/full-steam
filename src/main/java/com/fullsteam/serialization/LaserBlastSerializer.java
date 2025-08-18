package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.LaserBlast;

import java.io.IOException;

public class LaserBlastSerializer extends AbstractSerializer<LaserBlast> {
    @Override
    protected void serializeFields(LaserBlast value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeObjectField("start", value.getStart());
        gen.writeObjectField("end", value.getEnd());
        gen.writeNumberField("team", value.getTeam());
        gen.writeNumberField("expires", value.getExpires());
    }
}
