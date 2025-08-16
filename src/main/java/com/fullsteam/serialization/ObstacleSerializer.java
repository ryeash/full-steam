package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Obstacle;

import java.io.IOException;

public class ObstacleSerializer extends JsonSerializer<Obstacle> {

    @Override
    public void serialize(Obstacle obstacle, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("id", obstacle.id());
        
        gen.writeFieldName("vertices");
        gen.writeStartArray();
        for (var vertex : obstacle.getVertices()) {
            serializers.defaultSerializeValue(vertex, gen);
        }
        gen.writeEndArray();
        
        gen.writeBooleanField("rendered", obstacle.isRendered());
        
        gen.writeEndObject();
    }
}
