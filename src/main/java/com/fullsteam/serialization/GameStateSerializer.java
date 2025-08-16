package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.GameState;

import java.io.IOException;

public class GameStateSerializer extends JsonSerializer<GameState> {

    @Override
    public void serialize(GameState gameState, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeFieldName("players");
        gen.writeStartArray();
        for (var player : gameState.players()) {
            serializers.defaultSerializeValue(player, gen);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("bullets");
        gen.writeStartArray();
        for (var bullet : gameState.bullets()) {
            serializers.defaultSerializeValue(bullet, gen);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("fieldEffects");
        gen.writeStartArray();
        for (var fieldEffect : gameState.fieldEffects()) {
            serializers.defaultSerializeValue(fieldEffect, gen);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("turrets");
        gen.writeStartArray();
        for (var turret : gameState.turrets()) {
            serializers.defaultSerializeValue(turret, gen);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("obstacles");
        gen.writeStartArray();
        for (var obstacle : gameState.obstacles()) {
            serializers.defaultSerializeValue(obstacle, gen);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("powerUps");
        gen.writeStartArray();
        for (var powerUp : gameState.powerUps()) {
            serializers.defaultSerializeValue(powerUp, gen);
        }
        gen.writeEndArray();
        
        gen.writeNumberField("serverTime", gameState.serverTime());
        
        gen.writeFieldName("info");
        serializers.defaultSerializeValue(gameState.info(), gen);
        
        gen.writeEndObject();
    }
}
