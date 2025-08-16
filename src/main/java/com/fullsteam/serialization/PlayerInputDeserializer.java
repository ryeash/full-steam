package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fullsteam.model.PlayerInput;

import java.io.IOException;

public class PlayerInputDeserializer extends JsonDeserializer<PlayerInput> {

    @Override
    public PlayerInput deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        PlayerInput input = new PlayerInput();
        
        if (node.has("moveX")) {
            input.setMoveX(node.get("moveX").asDouble());
        }
        if (node.has("moveY")) {
            input.setMoveY(node.get("moveY").asDouble());
        }
        if (node.has("shooting")) {
            input.setShooting(node.get("shooting").asBoolean());
        }
        if (node.has("mouseX")) {
            input.setMouseX(node.get("mouseX").asDouble());
        }
        if (node.has("mouseY")) {
            input.setMouseY(node.get("mouseY").asDouble());
        }
        if (node.has("reload")) {
            input.setReload(node.get("reload").asBoolean());
        }
        if (node.has("placingObstacle")) {
            input.setPlacingObstacle(node.get("placingObstacle").asBoolean());
        }
        
        return input;
    }
}
