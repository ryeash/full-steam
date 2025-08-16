package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.GameEvent;

import java.io.IOException;

public class GameEventSerializer extends AbstractSerializer<GameEvent> {

    @Override
    protected void serializeFields(GameEvent gameEvent, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("message", gameEvent.message());
        gen.writeStringField("type", gameEvent.type().name());
        gen.writeNumberField("expirationTime", gameEvent.expirationTime());

        if (gameEvent.playerId() != null) {
            gen.writeNumberField("playerId", gameEvent.playerId());
        }
    }
}
