package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Vector2D;

import java.io.IOException;

public class ObstacleSerializer extends AbstractSerializer<Obstacle> {

    @Override
    public void serializeFields(Obstacle obstacle, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeArrayFieldStart("vertices");
        for (Vector2D vertex : obstacle.getVertices()) {
            serializers.defaultSerializeValue(vertex, gen);
        }
        gen.writeEndArray();
        gen.writeBooleanField("rendered", obstacle.isRendered());
    }
}
