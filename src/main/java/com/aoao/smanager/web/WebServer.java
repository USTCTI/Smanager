package com.aoao.smanager.web;

import com.aoao.smanager.monitor.MetricsSnapshot;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.ClassPathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import io.undertow.websockets.WebSocketConnectionCallback;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.spi.WebSocketHttpExchange;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class WebServer {
    private final Supplier<String> jsonSupplier;
    private final int port;
    private final String token;
    private final Logger logger;
    private Undertow server;
    private final Set<WebSocketChannel> channels = Collections.synchronizedSet(new HashSet<>());
    private ScheduledExecutorService scheduler;

    public WebServer(Supplier<String> jsonSupplier, int port, String token, Logger logger) {
        this.jsonSupplier = jsonSupplier;
        this.port = port;
        this.token = token == null ? "" : token.trim();
        this.logger = logger;
    }

    public void start() {
        ClassPathResourceManager resources = new ClassPathResourceManager(getClass().getClassLoader(), "web");
        ResourceHandler staticHandler = new ResourceHandler(resources).addWelcomeFiles("index.html");
        HttpHandler apiMetrics = exchange -> {
            if (!authorize(exchange)) return;
            String json = jsonSupplier.get();
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json;charset=utf-8");
            exchange.setStatusCode(StatusCodes.OK);
            exchange.getResponseSender().send(json);
        };
        PathHandler path = Handlers.path()
                .addPrefixPath("/api/metrics", apiMetrics)
                .addPrefixPath("/", staticHandler)
                .addExactPath("/api/health", exchange -> {
                    if (!authorize(exchange)) return;
                    exchange.setStatusCode(StatusCodes.OK);
                    exchange.getResponseSender().send("ok");
                });
        WebSocketConnectionCallback wsCallback = new WebSocketConnectionCallback() {
            @Override
            public void onConnect(WebSocketHttpExchange exchange, WebSocketChannel channel) {
                if (!authorizeWs(exchange)) {
                    try {
                        channel.sendClose();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                channels.add(channel);
                channel.addCloseTask(channels::remove);
                channel.resumeReceives();
            }
        };
        path.addPrefixPath("/ws", Handlers.websocket(wsCallback));
        server = Undertow.builder()
                .addHttpListener(port, "0.0.0.0")
                .setHandler(path)
                .build();
        server.start();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "smanager-ws");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::broadcast, 1, 1, TimeUnit.SECONDS);
        logger.info("SManager web server on " + port);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        synchronized (channels) {
            for (WebSocketChannel c : channels) {
                try {
                    c.sendClose();
                } catch (Exception ignored) {
                }
            }
            channels.clear();
        }
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }
    }

    private void broadcast() {
        String json = jsonSupplier.get();
        synchronized (channels) {
            for (WebSocketChannel c : channels) {
                try {
                    WebSockets.sendText(json, c, null);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private boolean authorize(HttpServerExchange exchange) {
        if (token.isEmpty()) return true;
        String header = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        Deque<String> dq = exchange.getQueryParameters().getOrDefault("token", new ArrayDeque<>());
        String q = dq.peekFirst();
        if (header != null && header.startsWith("Bearer ")) {
            String t = header.substring(7).trim();
            if (t.equals(token)) return true;
        }
        if (q != null && q.equals(token)) return true;
        exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
        exchange.getResponseSender().send("unauthorized");
        return false;
    }

    private boolean authorizeWs(WebSocketHttpExchange exchange) {
        if (token.isEmpty()) return true;
        List<String> list = exchange.getRequestParameters().get("token");
        String q = (list != null && !list.isEmpty()) ? list.get(0) : null;
        if (q != null && q.equals(token)) return true;
        return false;
    }
}
