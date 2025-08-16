package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.WelcomeMessage;

import java.io.IOException;

public class WelcomeMessageSerializer extends JsonSerializer<WelcomeMessage> {

    @Override
    public void serialize(WelcomeMessage welcomeMessage, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeStringField("type", welcomeMessage.type());
        gen.writeNumberField("playerId", welcomeMessage.playerId());
        gen.writeNumberField("team", welcomeMessage.team());
        
        if (welcomeMessage.gameId() != null) {
            gen.writeNumberField("gameId", welcomeMessage.gameId());
        }

        gen.writeArrayFieldStart("obstacles");
        for (Obstacle obstacle : welcomeMessage.obstacles()) {
            serializers.defaultSerializeValue(obstacle, gen);
        }
        gen.writeEndArray();
        
        gen.writeFieldName("weaponOptions");
        gen.writeStartArray();
        for (String weaponOption : welcomeMessage.weaponOptions()) {
            gen.writeString(weaponOption);
        }
        gen.writeEndArray();
        
        gen.writeEndObject();
    }
}
