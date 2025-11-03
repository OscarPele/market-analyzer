package com.oscar.market.metrics.derivatives.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class FuturesLiquidationWs implements ApplicationRunner {

    private final LiquidationIngestor ingestor;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${market.ws.enabled:true}")
    private boolean enabled;

    private static final URI STREAM = URI.create("wss://fstream.binance.com/ws/!forceOrder@arr");

    public FuturesLiquidationWs(LiquidationIngestor ingestor) {
        this.ingestor = ingestor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        connect();
    }

    private void connect() {
        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(STREAM, new Listener())
                .exceptionally(err -> {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    connect();
                    return null;
                });
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                String msg = buf.toString();
                buf.setLength(0);
                handle(msg);
            }
            ws.request(1);
            return CompletableFuture.completedStage(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            connect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connect();
            return CompletableFuture.completedStage(null);
        }
    }

    private void handle(String json) {
        try {
            JsonNode root = om.readTree(json);
            if (root.isArray()) {
                for (JsonNode n : root) ingest(n);
            } else {
                ingest(root);
            }
        } catch (Exception ignored) {}
    }

    private void ingest(JsonNode n) {
        JsonNode o = n.has("o") ? n.get("o") : n;

        String symbol = text(o, "s");
        if (symbol == null || symbol.isEmpty()) return;

        String side = text(o, "S"); // puede venir vacío

        double price = num(o, "p",
                num(o, "ap", 0.0));
        double qty   = num(o, "z",
                num(o, "q",
                        num(o, "l", 0.0)));

        long ts = numLong(o, "T",
                numLong(n, "E", System.currentTimeMillis()));

        if (price <= 0 || qty <= 0) return;

        double notional = price * qty;
        LiquidationEvent ev = new LiquidationEvent(null, symbol, side, price, qty, notional, ts);
        ingestor.accept(ev);
    }

    private String text(JsonNode n, String f) {
        JsonNode x = n.get(f);
        return x == null || x.isNull() ? null : x.asText();
    }
    private double num(JsonNode n, String f, double def) {
        JsonNode x = n.get(f);
        if (x == null || x.isNull()) return def;
        try { return Double.parseDouble(x.asText()); } catch (Exception e) { return def; }
    }
    private long numLong(JsonNode n, String f, long def) {
        JsonNode x = n.get(f);
        if (x == null || x.isNull()) return def;
        try { return Long.parseLong(x.asText()); } catch (Exception e) { return def; }
    }
}
