package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Player;

import java.io.IOException;

public class PlayerSerializer extends AbstractSerializer<Player> {

    @Override
    public void serializeFields(Player player, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStringField("playerName", player.getPlayerName());
        gen.writeNumberField("x", player.getX());
        gen.writeNumberField("y", player.getY());
        gen.writeNumberField("team", player.getTeam());

        gen.writeFieldName("weapon");
        serializers.defaultSerializeValue(player.getWeapon(), gen);

        gen.writeNumberField("hp", player.getHp());
        gen.writeNumberField("maxHp", player.getMaxHp());
        gen.writeNumberField("mouseX", player.getMouseX());
        gen.writeNumberField("mouseY", player.getMouseY());
        gen.writeBooleanField("dead", player.isDead());
        if (player.getRespawnTime() > System.currentTimeMillis()) {
            gen.writeNumberField("respawnTime", player.getRespawnTime());
        }
        gen.writeNumberField("kills", player.getKills());
        gen.writeNumberField("deaths", player.getDeaths());
        gen.writeNumberField("currentAmmoInMagazine", player.getCurrentAmmoInMagazine());
        gen.writeBooleanField("reloading", player.isReloading());
        gen.writeObjectField("vehicleId", player.getVehicleId());
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
    }
}
