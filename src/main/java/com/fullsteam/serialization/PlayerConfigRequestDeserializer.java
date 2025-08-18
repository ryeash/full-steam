package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fullsteam.model.PlayerConfigRequest;

import java.io.IOException;

public class PlayerConfigRequestDeserializer extends JsonDeserializer<PlayerConfigRequest> {

    @Override
    public PlayerConfigRequest deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        PlayerConfigRequest request = new PlayerConfigRequest();
        
        if (node.has("playerName")) {
            request.setPlayerName(node.get("playerName").asText());
        }
        if (node.has("weaponName")) {
            request.setWeaponName(node.get("weaponName").asText());
        }
        if (node.has("requestTeamChange")) {
            request.setRequestTeamChange(node.get("requestTeamChange").asBoolean());
        }
        
        return request;
    }
}
