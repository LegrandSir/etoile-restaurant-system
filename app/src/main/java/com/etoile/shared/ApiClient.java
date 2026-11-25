package com.etoile.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ApiClient {

    private static final String RAW_HOST = System.getProperty("etoile.host",
            System.getenv().getOrDefault("ETOILE_HOST", "localhost:8080")).trim();
    private static final boolean SECURE = RAW_HOST.startsWith("https://");
    private static final String CLEAN_HOST = RAW_HOST.replaceFirst("^https?://", "").replaceAll("/$", "");

    private static final String BASE_HTTP = (SECURE ? "https://" : "http://") + CLEAN_HOST;
    private static final String BASE_WS   = (SECURE ? "wss://" : "ws://") + CLEAN_HOST + "/ws/orders";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private volatile WebSocket activeSocket;
    private volatile boolean closed = false;

    public ApiClient() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public CompletableFuture<List<MenuItemDTO>> fetchMenu() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_HTTP + "/api/menu"))
                .GET()
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    try {
                        return mapper.readValue(res.body(), new TypeReference<List<MenuItemDTO>>() {});
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse menu", e);
                    }
                });
    }

    public CompletableFuture<List<OrderDTO>> fetchActiveOrders() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_HTTP + "/api/orders"))
                .GET()
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    try {
                        return mapper.readValue(res.body(), new TypeReference<List<OrderDTO>>() {});
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse active orders", e);
                    }
                });
    }

    public CompletableFuture<List<OrderDTO>> fetchTableOrders(int tableNumber) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_HTTP + "/api/orders/table/" + tableNumber))
                .GET()
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    try {
                        return mapper.readValue(res.body(), new TypeReference<List<OrderDTO>>() {});
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse table orders", e);
                    }
                });
    }

    public CompletableFuture<OrderDTO> submitOrder(OrderDTO order) {
        try {
            String json = mapper.writeValueAsString(order);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_HTTP + "/api/orders"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> {
                        try {
                            return mapper.readValue(res.body(), OrderDTO.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse created order", e);
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<OrderDTO> updateOrderStatus(Long orderId, OrderStatus status) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_HTTP + "/api/orders/" + orderId + "/status?status=" + status.name()))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    try {
                        return mapper.readValue(res.body(), OrderDTO.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to update order status", e);
                    }
                });
    }

    public CompletableFuture<OrderDTO> toggleOrderItem(Long orderId, Long itemId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_HTTP + "/api/orders/" + orderId + "/items/" + itemId + "/toggle"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    try {
                        return mapper.readValue(res.body(), OrderDTO.class);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to toggle item", e);
                    }
                });
    }

    public void connectWebSocket(Consumer<OrderDTO> onOrderUpdate, Consumer<Boolean> onConnectionChange) {
        if (closed) return;

        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create(BASE_WS), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        activeSocket = webSocket;
                        Platform.runLater(() -> onConnectionChange.accept(true));
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            String payload = buffer.toString();
                            buffer.setLength(0);
                            try {
                                OrderDTO order = mapper.readValue(payload, OrderDTO.class);
                                Platform.runLater(() -> onOrderUpdate.accept(order));
                            } catch (Exception ex) {
                                System.err.println("[WS-Client] Parse error: " + ex.getMessage());
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        Platform.runLater(() -> onConnectionChange.accept(false));
                        scheduleReconnect(onOrderUpdate, onConnectionChange);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        Platform.runLater(() -> onConnectionChange.accept(false));
                        scheduleReconnect(onOrderUpdate, onConnectionChange);
                    }
                }).exceptionally(ex -> {
                    Platform.runLater(() -> onConnectionChange.accept(false));
                    scheduleReconnect(onOrderUpdate, onConnectionChange);
                    return null;
                });
    }

    private void scheduleReconnect(Consumer<OrderDTO> onOrderUpdate, Consumer<Boolean> onConnectionChange) {
        if (!closed) {
            reconnectScheduler.schedule(() -> connectWebSocket(onOrderUpdate, onConnectionChange), 3, TimeUnit.SECONDS);
        }
    }

    public void shutdown() {
        closed = true;
        if (activeSocket != null) {
            try {
                activeSocket.sendClose(WebSocket.NORMAL_CLOSURE, "App closing");
            } catch (Exception ignored) {}
        }
        reconnectScheduler.shutdownNow();
    }
}