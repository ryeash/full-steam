package com.fullsteam.service;

import io.micronaut.websocket.WebSocketSession;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Minimal Channel implementation that bridges WebSocket sessions to the existing game code.
 * Only implements the methods actually used by the game logic.
 */
public class WebSocketChannelBridge implements Channel {

    private static final Logger log = LoggerFactory.getLogger(WebSocketChannelBridge.class);

    private final String sessionId;
    private final WebSocketSession webSocketSession;
    private final Long playerId;
    private final ConcurrentMap<AttributeKey<?>, Object> attributes = new ConcurrentHashMap<>();

    public WebSocketChannelBridge(String sessionId, WebSocketSession webSocketSession, Long playerId) {
        this.sessionId = sessionId;
        this.webSocketSession = webSocketSession;
        this.playerId = playerId;
    }

    // Channel ID implementation
    @Override
    public ChannelId id() {
        return new ChannelId() {
            @Override
            public String asShortText() {
                return sessionId.substring(0, Math.min(8, sessionId.length()));
            }

            @Override
            public String asLongText() {
                return sessionId;
            }

            @Override
            public int compareTo(ChannelId o) {
                return sessionId.compareTo(o.asLongText());
            }
        };
    }

    // Attribute management - used by game code to store player/game references
    @Override
    @SuppressWarnings("unchecked")
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        return new Attribute<T>() {
            @Override
            public AttributeKey<T> key() {
                return key;
            }

            @Override
            public T get() {
                return (T) attributes.get(key);
            }

            @Override
            public void set(T value) {
                attributes.put(key, value);
            }

            @Override
            public T getAndSet(T value) {
                return (T) attributes.put(key, value);
            }

            @Override
            public T setIfAbsent(T value) {
                return (T) attributes.putIfAbsent(key, value);
            }

            @Override
            public T getAndRemove() {
                return (T) attributes.remove(key);
            }

            @Override
            public boolean compareAndSet(T oldValue, T newValue) {
                return attributes.replace(key, oldValue, newValue);
            }

            @Override
            public void remove() {
                attributes.remove(key);
            }
        };
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return attributes.containsKey(key);
    }

    // Write operations - delegate to WebSocket session
    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                if (msg instanceof String) {
                    webSocketSession.sendSync(msg);
                } else if (msg instanceof BinaryWebSocketFrame frame) {
                    webSocketSession.sendSync(frame.content());
                } else {
                    webSocketSession.sendSync(msg);
                }
            } catch (Exception e) {
                log.error("Error sending message through WebSocket for player {}", playerId, e);
            }
        }
        return new DummyChannelFuture();
    }

    // Connection state
    @Override
    public boolean isActive() {
        return webSocketSession != null && webSocketSession.isOpen();
    }

    @Override
    public boolean isOpen() {
        return isActive();
    }

    // Remote address - used for logging
    @Override
    public SocketAddress remoteAddress() {
        return new SocketAddress() {
            @Override
            public String toString() {
                return "websocket-" + sessionId;
            }
        };
    }

    // Close operation
    @Override
    public ChannelFuture close() {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                webSocketSession.close();
            } catch (Exception e) {
                log.error("Error closing WebSocket session for player {}", playerId, e);
            }
        }
        return new DummyChannelFuture();
    }

    @Override
    public int compareTo(Channel o) {
        return id().compareTo(o.id());
    }

    // Minimal implementations for required methods - most throw UnsupportedOperationException
    // since they're not used by the game logic

    @Override
    public EventLoop eventLoop() {
        throw new UnsupportedOperationException("EventLoop not supported in WebSocket bridge");
    }

    @Override
    public Channel parent() {
        return null;
    }

    @Override
    public ChannelConfig config() {
        throw new UnsupportedOperationException("ChannelConfig not supported in WebSocket bridge");
    }

    @Override
    public SocketAddress localAddress() {
        return null;
    }

    @Override
    public boolean isRegistered() {
        return isActive();
    }

    @Override
    public boolean isWritable() {
        return isActive();
    }

    @Override
    public long bytesBeforeUnwritable() {
        return Long.MAX_VALUE;
    }

    @Override
    public long bytesBeforeWritable() {
        return 0;
    }

    @Override
    public Channel.Unsafe unsafe() {
        throw new UnsupportedOperationException("Unsafe not supported in WebSocket bridge");
    }

    @Override
    public ChannelPipeline pipeline() {
        throw new UnsupportedOperationException("ChannelPipeline not supported in WebSocket bridge");
    }

    @Override
    public ByteBufAllocator alloc() {
        throw new UnsupportedOperationException("ByteBufAllocator not supported in WebSocket bridge");
    }

    @Override
    public ChannelMetadata metadata() {
        throw new UnsupportedOperationException("ChannelMetadata not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture closeFuture() {
        return new DummyChannelFuture();
    }

    @Override
    public ChannelPromise newPromise() {
        throw new UnsupportedOperationException("ChannelPromise not supported in WebSocket bridge");
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        throw new UnsupportedOperationException("ChannelProgressivePromise not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        return new DummyChannelFuture();
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        return new DummyChannelFuture();
    }

    @Override
    public ChannelPromise voidPromise() {
        throw new UnsupportedOperationException("ChannelPromise not supported in WebSocket bridge");
    }

    // Binding and connection methods - not used
    @Override
    public ChannelFuture bind(SocketAddress localAddress) {
        throw new UnsupportedOperationException("bind not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress) {
        throw new UnsupportedOperationException("connect not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
        throw new UnsupportedOperationException("connect not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture disconnect() {
        return close();
    }

    @Override
    public ChannelFuture deregister() {
        return close();
    }

    @Override
    public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
        throw new UnsupportedOperationException("bind not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
        throw new UnsupportedOperationException("connect not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        throw new UnsupportedOperationException("connect not supported in WebSocket bridge");
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise promise) {
        return close();
    }

    @Override
    public ChannelFuture close(ChannelPromise promise) {
        return close();
    }

    @Override
    public ChannelFuture deregister(ChannelPromise promise) {
        return close();
    }

    @Override
    public Channel read() {
        return this;
    }

    @Override
    public ChannelFuture write(Object msg) {
        return writeAndFlush(msg);
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        return writeAndFlush(msg);
    }

    @Override
    public Channel flush() {
        return this;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        return writeAndFlush(msg);
    }

    /**
     * Minimal ChannelFuture implementation
     */
    private static class DummyChannelFuture implements ChannelFuture {
        @Override
        public Channel channel() {
            return null;
        }

        @Override
        public ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
            return this;
        }

        @Override
        public ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
            return this;
        }

        @Override
        public ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener) {
            return this;
        }

        @Override
        public ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
            return this;
        }

        @Override
        public ChannelFuture sync() {
            return this;
        }

        @Override
        public ChannelFuture syncUninterruptibly() {
            return this;
        }

        @Override
        public ChannelFuture await() {
            return this;
        }

        @Override
        public ChannelFuture awaitUninterruptibly() {
            return this;
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isCancellable() {
            return false;
        }

        @Override
        public Throwable cause() {
            return null;
        }

        @Override
        public boolean await(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public boolean await(long timeoutMillis) {
            return true;
        }

        @Override
        public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public boolean awaitUninterruptibly(long timeoutMillis) {
            return true;
        }

        @Override
        public Void getNow() {
            return null;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Void get() {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public boolean isVoid() {
            return false;
        }
    }
}
