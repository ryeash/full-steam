package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.GunMasterInfo;

import java.io.IOException;

public class GunMasterInfoSerializer extends AbstractSerializer<GunMasterInfo> {

    @Override
    public void serializeFields(GunMasterInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
