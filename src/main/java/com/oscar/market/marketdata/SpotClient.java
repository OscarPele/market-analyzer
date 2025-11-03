package com.oscar.market.marketdata;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpotClient {

    private final RestClient http;

    public SpotClient() {
        this.http = RestClient.builder()
                .baseUrl("https://api.binance.com")
                .build();
    }

    public List<Kline> getKlines(String symbol, String interval, Long startTime, Long endTime, Integer limit) {
        var builder = http.get()
                .uri(uri -> {
                    var b = uri.path("/api/v3/klines")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval);
                    if (startTime != null) b.queryParam("startTime", startTime);
                    if (endTime != null) b.queryParam("endTime", endTime);
                    if (limit != null) b.queryParam("limit", limit);
                    return b.build();
                })
                .accept(MediaType.APPLICATION_JSON);

        List<List<Object>> rows = builder.retrieve()
                .body(new ParameterizedTypeReference<>() {});

        List<Kline> out = new ArrayList<>();
        if (rows == null) return out;
        for (List<Object> r : rows) {
            try {
                long openTime  = asLong(r.get(0));
                double open    = asDouble(r.get(1));
                double high    = asDouble(r.get(2));
                double low     = asDouble(r.get(3));
                double close   = asDouble(r.get(4));
                double volume  = asDouble(r.get(5));
                long closeTime = asLong(r.get(6));
                out.add(new Kline(openTime, open, high, low, close, volume, closeTime));
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static record Kline(long openTime, double open, double high, double low, double close,
                               double volume, long closeTime) {}

    private static double asDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0.0; }
    }
    private static long asLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; }
    }
}
