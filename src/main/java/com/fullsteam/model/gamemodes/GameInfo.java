package com.fullsteam.model.gamemodes;

import io.micronaut.core.annotation.Introspected;

@Introspected
public abstract class GameInfo {
    public abstract String getType();
}