package com.fullsteam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fullsteam.model.GameEvent;
import com.fullsteam.model.Player;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

@MicronautTest
public class MiscTest {

    @Inject
    ObjectMapper mapper;

    @Test
    public void jsonTest() throws JsonProcessingException {
        System.out.println(mapper.writeValueAsString(GameEvent.info("toast")));
        System.out.println(mapper.writeValueAsString(new Player(
                1, "test", 123.3456, 653.22132, 1, WeaponFactory.getDefaultWeapon()
        )));
    }

    @Test
    public void foo() throws IOException {
        String s = "{\"players\":[{\"id\":11,\"name\":\"AI - Strudel\",\"x\":897.6,\"y\":577.3,\"team\":2,\"weapon\":{\"name\":\"Sniper Rifle\",\"shortName\":\"SR\"},\"hp\":12.0,\"maxHp\":100.0,\"mouseX\":800.1,\"mouseY\":613.5,\"kills\":6,\"deaths\":1,\"ammoInMag\":7},{\"id\":12,\"name\":\"AI - Pastel de Nata\",\"x\":661.9,\"y\":290.3,\"team\":2,\"weapon\":{\"name\":\"SMG\",\"shortName\":\"SMG\"},\"hp\":66.4,\"maxHp\":100.0,\"mouseX\":613.1,\"mouseY\":377.1,\"kills\":5,\"deaths\":3,\"ammoInMag\":20},{\"id\":9,\"name\":\"AI - Cassata\",\"x\":460.4,\"y\":517.4,\"team\":2,\"weapon\":{\"name\":\"Assault\",\"shortName\":\"A\"},\"hp\":100.0,\"maxHp\":100.0,\"mouseX\":532.0,\"mouseY\":446.7,\"kills\":3,\"deaths\":1,\"ammoInMag\":12},{\"id\":5,\"name\":\"AI - Sago\",\"x\":455.5,\"y\":245.2,\"team\":1,\"weapon\":{\"name\":\"Laser Pistol\",\"shortName\":\"LAZ\"},\"hp\":100.0,\"maxHp\":100.0,\"mouseX\":540.1,\"mouseY\":270.3,\"kills\":2,\"deaths\":3,\"ammoInMag\":10},{\"id\":6,\"name\":\"AI - Apple Strudel\",\"x\":622.2,\"y\":373.0,\"team\":1,\"weapon\":{\"name\":\"Hand Cannon\",\"shortName\":\"REV\"},\"hp\":23.2,\"maxHp\":100.0,\"mouseX\":664.2,\"mouseY\":281.7,\"kills\":2,\"deaths\":3,\"ammoInMag\":5},{\"id\":3,\"name\":\"AI - Whoopie Pie\",\"x\":459.2,\"y\":761.9,\"team\":1,\"weapon\":{\"name\":\"Hand Cannon\",\"shortName\":\"REV\"},\"hp\":100.0,\"maxHp\":100.0,\"mouseX\":0.0,\"mouseY\":0.0,\"kills\":1,\"deaths\":3,\"ammoInMag\":2},{\"id\":4,\"name\":\"AI - Brioche\",\"x\":434.3,\"y\":189.9,\"team\":1,\"weapon\":{\"name\":\"Laser Rifle\",\"shortName\":\"LR\"},\"hp\":23.2,\"maxHp\":100.0,\"mouseX\":440.6,\"mouseY\":291.9,\"kills\":1,\"deaths\":3,\"ammoInMag\":1},{\"id\":7,\"name\":\"AI - Gelato\",\"x\":441.5,\"y\":198.7,\"team\":1,\"weapon\":{\"name\":\"Laser Pistol\",\"shortName\":\"LAZ\"},\"hp\":44.0,\"maxHp\":100.0,\"mouseX\":532.6,\"mouseY\":236.1,\"deaths\":3,\"ammoInMag\":8},{\"id\":10,\"name\":\"AI - Gelato\",\"x\":703.0,\"y\":170.3,\"team\":2,\"weapon\":{\"name\":\"Laser Pistol\",\"shortName\":\"LAZ\"},\"hp\":100.0,\"maxHp\":100.0,\"mouseX\":670.5,\"mouseY\":253.9,\"deaths\":1,\"ammoInMag\":11},{\"id\":109,\"name\":\"King of Cake!\",\"x\":876.9,\"y\":684.8,\"team\":2,\"weapon\":{\"name\":\"Sniper Rifle\",\"shortName\":\"SR\"},\"hp\":100.0,\"maxHp\":100.0,\"mouseX\":1003.0,\"mouseY\":611.0,\"ammoInMag\":9}],\"bullets\":[{\"id\":390,\"team\":2,\"x\":590.3,\"y\":770.1},{\"id\":392,\"team\":2,\"x\":600.8,\"y\":329.3},{\"id\":395,\"team\":1,\"x\":680.4,\"y\":138.9},{\"id\":396,\"team\":2,\"x\":546.8,\"y\":375.3},{\"id\":398,\"team\":2,\"x\":630.8,\"y\":343.2},{\"id\":399,\"team\":2,\"x\":498.5,\"y\":473.2}],\"laserBlasts\":[{\"id\":397,\"start\":{\"x\":442.0,\"y\":198.3},\"end\":{\"x\":667.4,\"y\":346.9},\"team\":1,\"shooterId\":7,\"damage\":33.6,\"expires\":1756505819033},{\"id\":400,\"start\":{\"x\":438.9,\"y\":242.3},\"end\":{\"x\":659.7,\"y\":362.5},\"team\":1,\"shooterId\":5,\"damage\":33.6,\"expires\":1756505819079},{\"id\":401,\"start\":{\"x\":440.7,\"y\":192.5},\"end\":{\"x\":352.2,\"y\":846.5},\"team\":1,\"shooterId\":4,\"damage\":27.2,\"expires\":1756505819079}],\"serverTime\":1756505819027,\"info\":{\"team1Score\":6.0,\"team2Score\":15.0,\"timeLeft\":132,\"type\":\"Team Deathmatch\"}}";
        byte[] payload = s.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        gzip(new ByteArrayInputStream(payload), os);
        byte[] compressed = os.toByteArray();
        System.out.println("orig: " + payload.length);
        System.out.println("gzip: " + compressed.length);
    }

    private static final int BUFFER_SIZE = 512;

    public static void gzip(InputStream is, OutputStream os) throws IOException {
        GZIPOutputStream gzipOs = new GZIPOutputStream(os);
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead = 0;
        while ((bytesRead = is.read(buffer)) > -1) {
            gzipOs.write(buffer, 0, bytesRead);
        }
        gzipOs.close();
    }
}
