package com.fullsteam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fullsteam.games.AbstractGameStateManager;
import com.fullsteam.model.PlayerConfigRequest;
import com.fullsteam.model.PlayerInput;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fullsteam.Config.MAX_GLOBAL_PLAYERS;

public class GameWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);
    public static final AttributeKey<AbstractGameStateManager> GAME_STATE_MANAGER_KEY = AttributeKey.valueOf("gameStateManager");
    public static final AttributeKey<String> PLAYER_ID_KEY = AttributeKey.valueOf("playerId");
    public static final AttributeKey<Boolean> IS_SPECTATOR_KEY = AttributeKey.valueOf("isSpectator");

    private final GameLobby gameLobby;

    public GameWebSocketHandler(GameLobby gameLobby) {
        this.gameLobby = gameLobby;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete handshake) {
            String uri = handshake.requestUri();
            // URI format for players: /game/{gameId}/{gameType}
            // URI format for spectators: /game/spectate/{gameId}
            String[] parts = StringUtils.split(uri, '/');

            // 1. Check with the lobby if a new player can be accepted.
            if (!gameLobby.tryAcceptNewPlayer()) {
                log.warn("Server is full ({} players). Rejecting new connection from {}.",
                        MAX_GLOBAL_PLAYERS, ctx.channel().remoteAddress());
                ctx.channel().close();
                return;
            }

            try {
                if (parts.length > 2 && "spectate".equals(parts[2])) {
                    String gameId = parts[1];
                    gameLobby.spectateGame(ctx.channel(), gameId);
                } else if (parts.length > 0 && "game".equals(parts[0]) && parts.length > 2) {
                    String gameId = parts[1];
                    String gameType = parts[2];
                    gameLobby.joinGame(ctx.channel(), gameId, gameType);
                } else {
                    throw new IllegalArgumentException("Invalid connection URI: " + uri);
                }
            } catch (Exception e) {
                // If anything goes wrong during setup, make sure to decrement the player count.
                gameLobby.playerDisconnected();
                log.error("Error during connection setup for URI {}. Reverting player count.", uri, e);
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // channelActive is called when the TCP connection is established, before the
        // WebSocket handshake. We'll let userEventTriggered handle the logic.
        log.info("Channel {} became active, awaiting handshake.", ctx.channel().id());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Clean up the player from their specific game
        AbstractGameStateManager game = ctx.channel().attr(GAME_STATE_MANAGER_KEY).get();
        if (game != null) {
            Boolean isSpectator = ctx.channel().attr(IS_SPECTATOR_KEY).get();
            if (isSpectator != null && isSpectator) {
                game.removeSpectator(ctx.channel());
            } else {
                String playerId = ctx.channel().attr(PLAYER_ID_KEY).get();
                if (playerId != null) {
                    game.removePlayer(playerId);
                }
            }
            gameLobby.playerDisconnected();
            log.info("Connection from {} closed. Global players: {}", ctx.channel().remoteAddress(), gameLobby.getGlobalPlayerCount());
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        // Retrieve the correct GameStateManager and Player ID from the channel's attributes
        AbstractGameStateManager game = ctx.channel().attr(GAME_STATE_MANAGER_KEY).get();
        if (ctx.channel().hasAttr(IS_SPECTATOR_KEY)) {
            return; // Spectators don't send input
        }
        String playerId = ctx.channel().attr(PLAYER_ID_KEY).get();

        if (game == null || playerId == null) {
            log.warn("Received message from a channel without a game session. Closing.");
            ctx.close();
            return;
        }

        // The rest of the logic is the same, but operates on the correct game instance
        JsonNode rootNode = Jackson.readTree(msg.text());
        String type = rootNode.path("type").asText("playerInput");

        switch (type) {
            case "ping":
                // Immediately send a pong message back to the client's channel.
                // The content can be simple; the 'type' is what matters.
                String pongMessage = "{\"type\":\"pong\"}";
                ctx.channel().writeAndFlush(new TextWebSocketFrame(pongMessage));
                break;
            case "playerInput":
                PlayerInput input = Jackson.treeToValue(rootNode, PlayerInput.class);
                game.acceptPlayerInput(playerId, input);
                break;
            case "configChange":
                PlayerConfigRequest request = Jackson.treeToValue(rootNode, PlayerConfigRequest.class);
                game.handlePlayerConfigChange(playerId, request);
                break;
            default:
                log.warn("Received unknown message type '{}' from player {}", type, playerId);
                break;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String playerId = playerId(ctx);
        log.error("WebSocket error for player {}: {}", playerId, cause.getMessage());
        ctx.close();
    }

    public static String playerId(ChannelHandlerContext ctx) {
        return ctx.channel().id().asShortText();
    }

    public static String playerId(Channel ch) {
        return ch.id().asShortText();
    }
}
