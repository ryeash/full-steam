package com.fullsteam.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fullsteam.model.*;

public class CustomSerializationModule extends SimpleModule {

    public CustomSerializationModule() {
        super("CustomSerializationModule");
        
        // Register serializers
        addSerializer(GameState.class, new GameStateSerializer());
        addSerializer(Player.class, new PlayerSerializer());
        addSerializer(Bullet.class, new BulletSerializer());
        addSerializer(Vector2D.class, new Vector2DSerializer());
        addSerializer(Weapon.class, new WeaponSerializer());
        addSerializer(PowerUp.class, new PowerUpSerializer());
        addSerializer(Obstacle.class, new ObstacleSerializer());
        addSerializer(Turret.class, new TurretSerializer());
        addSerializer(GameEvent.class, new GameEventSerializer());
        addSerializer(WelcomeMessage.class, new WelcomeMessageSerializer());
        addSerializer(FieldEffect.class, new FieldEffectSerializer());
        
        // Register deserializers
        addDeserializer(PlayerInput.class, new PlayerInputDeserializer());
        addDeserializer(PlayerConfigRequest.class, new PlayerConfigRequestDeserializer());
        addDeserializer(Vector2D.class, new Vector2DDeserializer());
    }
}
