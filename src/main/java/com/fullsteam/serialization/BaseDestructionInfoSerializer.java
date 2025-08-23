package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.BaseDestructionInfo;

import java.io.IOException;

public class BaseDestructionInfoSerializer extends AbstractSerializer<BaseDestructionInfo> {
    @Override
    protected void serializeFields(BaseDestructionInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        
        gen.writeFieldName("base");
        serializers.defaultSerializeValue(info.getBase(), gen);
        
        gen.writeNumberField("timeLeft", info.gettimeLeft());
        gen.writeBooleanField("baseDestroyed", info.isBaseDestroyed());
    }
}
