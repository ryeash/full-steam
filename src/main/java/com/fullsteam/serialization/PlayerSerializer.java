package com.fullsteam.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fullsteam.model.Player;

import java.io.IOException;

public class PlayerSerializer extends JsonSerializer<Player> {

    @Override
    public void serialize(Player player, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        
        gen.writeNumberField("id", player.id());
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
        gen.writeBooleanField("isDead", player.isDead());
        gen.writeNumberField("respawnTime", player.getRespawnTime());
        gen.writeNumberField("kills", player.getKills());
        gen.writeNumberField("deaths", player.getDeaths());
        gen.writeNumberField("currentAmmoInMagazine", player.getCurrentAmmoInMagazine());
        gen.writeBooleanField("isReloading", player.isReloading());
        gen.writeNumberField("speedBoostEndTime", player.speedBoostEndTime);
        gen.writeNumberField("armorUpEndTime", player.armorUpEndTime);
        gen.writeNumberField("damageBoostEndTime", player.damageBoostEndTime);
        gen.writeNumberField("invisibilityEndTime", player.invisibilityEndTime);
        
        gen.writeEndObject();
    }
}
