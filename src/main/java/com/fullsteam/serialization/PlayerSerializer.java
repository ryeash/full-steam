package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Player;

import java.io.IOException;

import static com.fullsteam.serialization.CustomSerializationModule.withPrecision;

public class PlayerSerializer extends JsonSerializer<Player> {

    @Override
    public void serialize(Player player, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();

        gen.writeNumberField("id", player.id());
        gen.writeStringField("playerName", player.getPlayerName());
        gen.writeNumberField("x", withPrecision(player.getX()));
        gen.writeNumberField("y", withPrecision(player.getY()));
        gen.writeNumberField("team", player.getTeam());

        gen.writeFieldName("weapon");
        serializers.defaultSerializeValue(player.getWeapon(), gen);

        gen.writeNumberField("hp", player.getHp());
        gen.writeNumberField("maxHp", player.getMaxHp());
        gen.writeNumberField("mouseX", withPrecision(player.getMouseX()));
        gen.writeNumberField("mouseY", withPrecision(player.getMouseY()));
        gen.writeBooleanField("dead", player.isDead());
        gen.writeNumberField("respawnTime", player.getRespawnTime());
        gen.writeNumberField("kills", player.getKills());
        gen.writeNumberField("deaths", player.getDeaths());
        gen.writeNumberField("currentAmmoInMagazine", player.getCurrentAmmoInMagazine());
        gen.writeBooleanField("reloading", player.isReloading());
        if (player.speedBoostEndTime > System.currentTimeMillis()) {
            gen.writeNumberField("speedBoostEndTime", player.speedBoostEndTime);
        }
        if (player.armorUpEndTime > System.currentTimeMillis()) {
            gen.writeNumberField("armorUpEndTime", player.armorUpEndTime);
        }
        if (player.damageBoostEndTime > System.currentTimeMillis()) {
            gen.writeNumberField("damageBoostEndTime", player.damageBoostEndTime);
        }
        if (player.invisibilityEndTime > System.currentTimeMillis()) {
            gen.writeNumberField("invisibilityEndTime", player.invisibilityEndTime);
        }

        gen.writeEndObject();
    }
}
