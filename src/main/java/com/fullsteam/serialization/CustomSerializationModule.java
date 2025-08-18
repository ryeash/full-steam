package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fullsteam.model.ActiveGame;
import com.fullsteam.model.Base;
import com.fullsteam.model.Bullet;
import com.fullsteam.model.Crate;
import com.fullsteam.model.FieldEffect;
import com.fullsteam.model.Flag;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.GameState;
import com.fullsteam.model.Hill;
import com.fullsteam.model.LaserBlast;
import com.fullsteam.model.LobbyInfo;
import com.fullsteam.model.Obstacle;
import com.fullsteam.model.Oddball;
import com.fullsteam.model.Player;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import com.fullsteam.model.PowerUp;
import com.fullsteam.model.Turret;
import com.fullsteam.model.Vector2D;
import com.fullsteam.model.Weapon;
import com.fullsteam.model.WelcomeMessage;
import com.fullsteam.model.gamemodes.BuilderGameInfo;
import com.fullsteam.model.gamemodes.CaptureTheFlagInfo;
import com.fullsteam.model.gamemodes.EliminationInfo;
import com.fullsteam.model.gamemodes.EscortGameInfo;
import com.fullsteam.model.gamemodes.FreeForAllInfo;
import com.fullsteam.model.gamemodes.GunMasterInfo;
import com.fullsteam.model.gamemodes.JuggernautInfo;
import com.fullsteam.model.gamemodes.KingOfTheHillInfo;
import com.fullsteam.model.gamemodes.LoneWolfInfo;
import com.fullsteam.model.gamemodes.OddballInfo;
import com.fullsteam.model.gamemodes.TeamDeathmatchInfo;
import com.fullsteam.model.gamemodes.ZombieDefenseInfo;
import com.fullsteam.model.gamemodes.BaseDestructionInfo;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class CustomSerializationModule extends SimpleModule {

    public CustomSerializationModule() {
        super("CustomSerializationModule");

        addSerializer(Double.class, new ShorterDoubleSerializer());

        // Register serializers
        addSerializer(GameState.class, new GameStateSerializer());
        addSerializer(Player.class, new PlayerSerializer());
        addSerializer(Bullet.class, new BulletSerializer());
        addSerializer(LaserBlast.class, new LaserBlastSerializer());
        addSerializer(Vector2D.class, new Vector2DSerializer());
        addSerializer(Weapon.class, new WeaponSerializer());
        addSerializer(PowerUp.class, new PowerUpSerializer());
        addSerializer(Obstacle.class, new ObstacleSerializer());
        addSerializer(Turret.class, new TurretSerializer());
        addSerializer(GameEvent.class, new GameEventSerializer());
        addSerializer(WelcomeMessage.class, new WelcomeMessageSerializer());
        addSerializer(FieldEffect.class, new FieldEffectSerializer());
        addSerializer(LobbyInfo.class, new LobbyInfoSerializer());
        addSerializer(ActiveGame.class, new ActiveGameSerializer());

        // GameInfo and related objects
        addSerializer(Flag.class, new FlagSerializer());
        addSerializer(Hill.class, new HillSerializer());
        addSerializer(Oddball.class, new OddballSerializer());
        addSerializer(Crate.class, new CrateSerializer());
        addSerializer(Base.class, new BaseSerializer());

        // GameInfo subclasses
        addSerializer(TeamDeathmatchInfo.class, new TeamDeathmatchInfoSerializer());
        addSerializer(CaptureTheFlagInfo.class, new CaptureTheFlagInfoSerializer());
        addSerializer(FreeForAllInfo.class, new FreeForAllInfoSerializer());
        addSerializer(KingOfTheHillInfo.class, new KingOfTheHillInfoSerializer());
        addSerializer(OddballInfo.class, new OddballInfoSerializer());
        addSerializer(EliminationInfo.class, new EliminationInfoSerializer());
        addSerializer(JuggernautInfo.class, new JuggernautInfoSerializer());
        addSerializer(GunMasterInfo.class, new GunMasterInfoSerializer());
        addSerializer(LoneWolfInfo.class, new LoneWolfInfoSerializer());
        addSerializer(BuilderGameInfo.class, new BuilderGameInfoSerializer());
        addSerializer(EscortGameInfo.class, new EscortGameInfoSerializer());
        addSerializer(ZombieDefenseInfo.class, new ZombieDefenseInfoSerializer());
        addSerializer(BaseDestructionInfo.class, new BaseDestructionInfoSerializer());

        // Register deserializers
        addDeserializer(PlayerInput.class, new PlayerInputDeserializer());
        addDeserializer(PlayerConfigRequest.class, new PlayerConfigRequestDeserializer());
        addDeserializer(Vector2D.class, new Vector2DDeserializer());
    }

    private static final class ShorterDoubleSerializer extends StdSerializer<Double> {
        protected ShorterDoubleSerializer() {
            super(Double.class);
        }

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNumber(withPrecision(value, 4));
        }
    }

    public static Double withPrecision(Double value) {
        return withPrecision(value, 2);
    }

    public static Double withPrecision(Double value, int precision) {
        if (value == null) {
            return null;
        }
        BigDecimal bd = BigDecimal.valueOf(value)
                .setScale(precision, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
