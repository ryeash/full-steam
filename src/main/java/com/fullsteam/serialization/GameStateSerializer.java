package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.GameState;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.Player;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.Turret;

import java.io.IOException;

public class GameStateSerializer extends AbstractSerializer<GameState> {
    @Override
    public void serializeFields(GameState gameState, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeArrayFieldStart("players");
        for (Player player : gameState.players()) {
            serializers.defaultSerializeValue(player, gen);
        }
        gen.writeEndArray();

        gen.writeArrayFieldStart("bullets");
        for (Bullet bullet : gameState.bullets()) {
            serializers.defaultSerializeValue(bullet, gen);
        }
        gen.writeEndArray();

        gen.writeArrayFieldStart("laserBlasts");
        for (LaserBlast laserBlast : gameState.laserBlasts()) {
            serializers.defaultSerializeValue(laserBlast, gen);
        }
        gen.writeEndArray();

        gen.writeArrayFieldStart("fieldEffects");
        for (FieldEffect fieldEffect : gameState.fieldEffects()) {
            serializers.defaultSerializeValue(fieldEffect, gen);
        }
        gen.writeEndArray();

        gen.writeArrayFieldStart("turrets");

        for (Turret turret : gameState.turrets()) {
            serializers.defaultSerializeValue(turret, gen);
        }
        gen.writeEndArray();

        // no longer sending obstacles in GameState, moved to the welcome message
//        gen.writeArrayFieldStart("obstacles");
//        for (Obstacle obstacle : gameState.obstacles()) {
//            serializers.defaultSerializeValue(obstacle, gen);
//        }
//        gen.writeEndArray();

        gen.writeArrayFieldStart("powerUps");
        for (PowerUp powerUp : gameState.powerUps()) {
            serializers.defaultSerializeValue(powerUp, gen);
        }
        gen.writeEndArray();

        gen.writeNumberField("serverTime", gameState.serverTime());

        gen.writeFieldName("info");
        serializers.defaultSerializeValue(gameState.info(), gen);
    }
}
