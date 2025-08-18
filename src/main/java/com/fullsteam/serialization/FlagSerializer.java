package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Flag;

import java.io.IOException;

public class FlagSerializer extends AbstractSerializer<Flag> {
    @Override
    protected void serializeFields(Flag flag, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumberField("team", flag.team());
        gen.writeStringField("state", flag.state().name());

        gen.writeFieldName("position");
        serializers.defaultSerializeValue(flag.position(), gen);

        gen.writeFieldName("basePosition");
        serializers.defaultSerializeValue(flag.basePosition(), gen);

        if (flag.carrierId() != null) {
            gen.writeNumberField("carrierId", flag.carrierId());
        }
    }
}
