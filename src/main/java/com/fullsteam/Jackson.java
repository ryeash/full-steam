package com.fullsteam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.UncheckedIOException;

public class Jackson {

    public static final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setDefaultMergeable(true);

    public static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory())
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setDefaultMergeable(true);


    public static String writeValueAsString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ByteBuf msgPack(Object value) {
        try {
            return Unpooled.wrappedBuffer(MSGPACK_MAPPER.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to MessagePack", e);
        }
    }

    public static BinaryWebSocketFrame msgPackFrame(Object value) {
        return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(msgPack(value)));
    }

    public static JsonNode readTree(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static <T> T treeToValue(JsonNode rootNode, Class<T> type) {
        try {
            return objectMapper.treeToValue(rootNode, type);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
