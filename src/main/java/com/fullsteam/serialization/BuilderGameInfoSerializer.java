package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Crate;
import com.fullsteam.model.gamemodes.BuilderGameInfo;

import java.io.IOException;

public class BuilderGameInfoSerializer extends AbstractSerializer<BuilderGameInfo> {

    @Override
    protected void serializeFields(BuilderGameInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeArrayFieldStart("crates");
        for (Crate crate : info.getCrates()) {
            serializers.defaultSerializeValue(crate, gen);
        }
        gen.writeEndArray();
    }
}
