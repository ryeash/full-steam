package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.HasId;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class AbstractSerializer<T> extends JsonSerializer<T> {

    @Override
    public final void serialize(T value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        if (value instanceof HasId) {
            gen.writeNumberField("id", ((HasId) value).getId());
        }
        serializeFields(value, new DoubleCustomizingJsonGenerator(gen), serializers);
        gen.writeEndObject();
    }

    protected abstract void serializeFields(T value, JsonGenerator gen, SerializerProvider serializers) throws IOException;

    protected static final class DoubleCustomizingJsonGenerator extends JsonGeneratorDelegate {

        public DoubleCustomizingJsonGenerator(JsonGenerator d) {
            super(d);
        }

        @Override
        public void writeNumberField(String fieldName, double value) throws IOException {
            delegate.writeNumberField(fieldName, withPrecision(value));
        }

        public Double withPrecision(Double value) {
            return withPrecision(value, 2);
        }

        public Double withPrecision(Double value, int precision) {
            if (value == null) {
                return null;
            }
            BigDecimal bd = BigDecimal.valueOf(value)
                    .setScale(precision, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }


    }
}
