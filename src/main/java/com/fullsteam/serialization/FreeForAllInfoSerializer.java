package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.FreeForAllInfo;

import java.io.IOException;

public class FreeForAllInfoSerializer extends AbstractSerializer<FreeForAllInfo> {

    @Override
    protected void serializeFields(FreeForAllInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
