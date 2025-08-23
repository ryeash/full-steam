package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.JuggernautInfo;

import java.io.IOException;

public class JuggernautInfoSerializer extends AbstractSerializer<JuggernautInfo> {

    @Override
    public void serializeFields(JuggernautInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        
        if (info.getTeam1Juggernaut() != null) {
            gen.writeNumberField("team1Juggernaut", info.getTeam1Juggernaut());
        }
        if (info.getTeam2Juggernaut() != null) {
            gen.writeNumberField("team2Juggernaut", info.getTeam2Juggernaut());
        }
        
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
