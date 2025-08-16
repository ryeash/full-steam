package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Crate;

import java.io.IOException;

public class CrateSerializer extends JsonSerializer<Crate> {

    @Override
    public void serialize(Crate crate, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("id", crate.id());
        gen.writeNumberField("x", crate.getX());
        gen.writeNumberField("y", crate.getY());
        gen.writeNumberField("size", crate.getSize());
        gen.writeNumberField("hp", crate.getHp());
        gen.writeNumberField("maxHp", crate.getMaxHp());
        gen.writeBooleanField("rendered", crate.isRendered());
        
        gen.writeFieldName("vertices");
        gen.writeStartArray();
        for (var vertex : crate.getVertices()) {
            serializers.defaultSerializeValue(vertex, gen);
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
