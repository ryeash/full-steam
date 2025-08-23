package com.fullsteam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.runtime.Micronaut;
import jakarta.inject.Singleton;

public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Singleton
    static class ObjectMapperBeanEventListener implements BeanCreatedEventListener<ObjectMapper> {

        @Override
        public ObjectMapper onCreated(BeanCreatedEvent<ObjectMapper> event) {
            return event.getBean()
                    .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                    .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
//            .configOverride(Double.class).setFormat()
                    .setDefaultMergeable(true);
        }
    }
}
