package com.fullsteam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
public class MiscTest {

    @Inject
    ObjectMapper mapper;

    @Test
    public void jsonTest() throws JsonProcessingException {
        System.out.println(Jackson.writeValueAsString(GameEvent.info("toast")));
        System.out.println(mapper.writeValueAsString(new Player(
                1, "test", 123.3456, 653.22132, 1, WeaponFactory.getDefaultWeapon()
        )));
    }
}
