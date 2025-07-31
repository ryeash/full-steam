package com.fullsteam;

import com.fullsteam.model.GameEvent;
import org.junit.jupiter.api.Test;

public class MiscTest {

    @Test
    public void jsonTest(){
        System.out.println(Jackson.writeValueAsString(GameEvent.info("toast")));
    }
}
