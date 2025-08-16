package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.ZombieDefenseInfo;

import java.io.IOException;

public class ZombieDefenseInfoSerializer extends JsonSerializer<ZombieDefenseInfo> {

    @Override
    public void serialize(ZombieDefenseInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("waveNumber", info.getWaveNumber());
        gen.writeNumberField("zombiesAlive", info.getZombiesAlive());
        gen.writeNumberField("timeUntilNextWave", info.getTimeUntilNextWave());
        gen.writeNumberField("roundTimeRemainingSeconds", info.getRoundTimeRemainingSeconds());
        
        gen.writeEndObject();
    }
}
