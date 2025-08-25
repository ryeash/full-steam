package com.fullsteam.controller;

import com.fullsteam.GameLobby;
import com.fullsteam.config.GameConfig;
import com.fullsteam.model.LobbyInfo;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.types.files.StreamedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

@Singleton
@Controller
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameLobby gameLobby;
    private final GameConfig gameConfig;
    private final ResourceResolver resourceResolver;

    @Inject
    public GameController(GameLobby gameLobby, GameConfig gameConfig, ResourceResolver resourceResolver) {
        this.gameLobby = gameLobby;
        this.gameConfig = gameConfig;
        this.resourceResolver = resourceResolver;
    }

    @Get("/api/games")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public LobbyInfo getGames() {
        return new LobbyInfo(
                gameLobby.getGlobalPlayerCount(),
                gameConfig.getMaxGlobalPlayers(),
                gameLobby.getGameTypes(),
                gameLobby.getActiveGames()
        );
    }

    @Get(produces = MediaType.TEXT_HTML)
    public HttpResponse<StreamedFile> index() {
        return serveStaticFile("lobby.html", MediaType.TEXT_HTML);
    }

    @Get(value = "/game.html", produces = MediaType.TEXT_HTML)
    public HttpResponse<StreamedFile> game() {
        return serveStaticFile("game.html", MediaType.TEXT_HTML);
    }

    @Get(value = "/color-palette.js", produces = MediaType.TEXT_HTML)
    public HttpResponse<StreamedFile> colorPalette() {
        return serveStaticFile("color-palette.js", MediaType.TEXT_HTML);
    }

    @Get(value = "/favicon.ico", produces = MediaType.TEXT_HTML)
    public HttpResponse<StreamedFile> favicon() {
        return serveStaticFile("favicon.ico", MediaType.IMAGE_PNG);
    }

    private HttpResponse<StreamedFile> serveStaticFile(String path, String contentType) {
        try {
            Optional<URL> resource = resourceResolver.getResource("classpath:" + path);
            if (resource.isPresent()) {
                InputStream inputStream = resource.get().openStream();
                return HttpResponse.ok(new StreamedFile(inputStream, MediaType.of(contentType)));
            } else {
                log.warn("Resource not found: {}", path);
                return HttpResponse.notFound();
            }
        } catch (Exception e) {
            log.error("Error serving static file: {}", path, e);
            return HttpResponse.serverError();
        }
    }
}
