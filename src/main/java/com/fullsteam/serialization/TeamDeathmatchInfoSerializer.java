package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.gamemodes.TeamDeathmatchInfo;

import java.io.IOException;

public class TeamDeathmatchInfoSerializer extends AbstractSerializer<TeamDeathmatchInfo> {

    @Override
    public void serializeFields(TeamDeathmatchInfo info, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("type", info.getType());
        gen.writeNumberField("team1Score", info.getTeam1Score());
        gen.writeNumberField("team2Score", info.getTeam2Score());
        gen.writeNumberField("timeLeft", info.getTimeLeft());
    }
}
