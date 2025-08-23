package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.ZombieDefenseInfo;

import java.io.IOException;

public class ZombieDefenseInfoSerializer extends AbstractSerializer<ZombieDefenseInfo> {

    @Override
    public void serializeFields(ZombieDefenseInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("waveNumber", info.getWaveNumber());
        gen.writeNumberField("zombiesAlive", info.getZombiesAlive());
        gen.writeNumberField("timeUntilNextWave", info.getTimeUntilNextWave());
        gen.writeNumberField("timeLeft", info.gettimeLeft());
    }
}
