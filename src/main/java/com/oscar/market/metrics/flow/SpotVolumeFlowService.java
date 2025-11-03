package com.oscar.market.metrics.flow;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oscar.market.marketdata.BinanceClient;
import com.oscar.market.marketdata.Candle;

@Service
public class SpotVolumeFlowService {

    private final BinanceClient client;
    private final RestClient rest;

    public SpotVolumeFlowService(BinanceClient client) {
        this.client = client;
        // RestClient simple a api.binance.com con timeouts suaves
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6_000);
        factory.setReadTimeout(10_000);
        this.rest = RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://api.binance.com")
                .build();
    }

    /* =================== 14) Volumen vs MA20 (1h) =================== */

    public record VolumeMA20Result(double lastVolume, double ma20, double ratio, String state) {}

    public VolumeMA20Result volumeVsMA20(String symbol, String interval) {
        int lookback = 60; // suficiente para estabilizar
        List<Candle> candles = client.getKlines(symbol, interval, lookback);
        if (candles.size() < 21) {
            throw new IllegalArgumentException("Datos insuficientes para MA20 de volumen");
        }
        // tomamos las 20 velas ANTERIORES a la última para la media
        int n = candles.size();
        double sum = 0.0;
        for (int i = n - 21; i < n - 1; i++) sum += candles.get(i).volume();
        double ma20 = sum / 20.0;

        double lastVol = candles.get(n - 1).volume();
        double ratio = ma20 == 0.0 ? 0.0 : lastVol / ma20;

        String state;
        if (ratio >= 1.2) state = "high";
        else if (ratio <= 0.8) state = "low";
        else state = "normal";

        return new VolumeMA20Result(lastVol, ma20, ratio, state);
    }

    /* =================== 15) OBV pendiente (1h) =================== */

    public record ObvSlopeResult(double obv, double deltaPerBar, double pctPerBar, String sign) {}

    public ObvSlopeResult obvSlope(String symbol, String interval) {
        int limit = 400;
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        if (candles.size() < 3) throw new IllegalArgumentException("Datos insuficientes para OBV");

        // OBV clásico
        double obv = 0.0;
        for (int i = 1; i < candles.size(); i++) {
            double prevClose = candles.get(i - 1).close();
            double close = candles.get(i).close();
            double vol = candles.get(i).volume();
            if (close > prevClose) obv += vol;
            else if (close < prevClose) obv -= vol;
            // igual → obv sin cambios
        }

        // delta por vela = diferencia entre OBV último y el inmediatamente anterior
        // Para calcular obvPrev, repetimos pero hasta penúltima barra
        double obvPrev = 0.0;
        for (int i = 1; i < candles.size() - 1; i++) {
            double prevClose = candles.get(i - 1).close();
            double close = candles.get(i).close();
            double vol = candles.get(i).volume();
            if (close > prevClose) obvPrev += vol;
            else if (close < prevClose) obvPrev -= vol;
        }

        double delta = obv - obvPrev; // per última barra
        double pct = obvPrev != 0.0 ? (delta / Math.abs(obvPrev)) * 100.0 : 0.0;
        String sign = Math.abs(delta) < 1e-9 ? "flat" : (delta > 0 ? "up" : "down");

        return new ObvSlopeResult(obv, delta, pct, sign);
    }

    /* =================== 16) CVD (1h) y 17) Buy/Sell Ratio (1h) =================== */

    // aggTrades DTO (spot)
    public static record AggTradeDTO(
            @JsonProperty("p") String price,
            @JsonProperty("q") String qty,
            @JsonProperty("m") boolean isBuyerMaker,
            @JsonProperty("T") long tradeTime
    ) {}

    public record Flow1hResult(double buysVolume, double sellsVolume, double cvd, double buySellRatioPct) {}

    public Flow1hResult flowLastHour(String symbol) {
        long end = System.currentTimeMillis();
        long start = end - 60L * 60_000L; // última hora
        List<AggTradeDTO> trades = getAggTrades(symbol, start, end);

        double buys = 0.0;
        double sells = 0.0;
        for (AggTradeDTO t : trades) {
            double q = safeParseDouble(t.qty);
            // isBuyerMaker = true ⇒ el buyer es maker ⇒ la agresión la hace el vendedor ⇒ venta agresiva
            if (t.isBuyerMaker) sells += q; else buys += q;
        }
        double cvd = buys - sells;
        double denom = buys + sells;
        double ratio = denom == 0.0 ? 0.0 : (buys / denom) * 100.0;

        return new Flow1hResult(buys, sells, cvd, ratio);
    }

    private List<AggTradeDTO> getAggTrades(String symbol, long start, long end) {
        var ref = new ParameterizedTypeReference<List<AggTradeDTO>>() {};
        List<AggTradeDTO> trades = rest.get()
                .uri(uri -> uri.path("/api/v3/aggTrades")
                        .queryParam("symbol", symbol)
                        .queryParam("startTime", start)
                        .queryParam("endTime", end)
                        .build())
                .retrieve()
                .body(ref);
        return trades != null ? trades : List.of();
    }

    /* =================== 18) Order book imbalance =================== */

    public static record DepthDTO(
            List<List<String>> bids,
            List<List<String>> asks
    ) {}

    public record OrderbookImbalanceResult(
            int levels,
            double bidVolume,
            double askVolume,
            double imbalancePct   // (bid-ask)/(bid+ask)*100
    ) {}

    public OrderbookImbalanceResult orderbookImbalance(String symbol, int levels) {
        int limit = clampDepthLimit(levels);
        DepthDTO depth = rest.get()
                .uri(uri -> uri.path("/api/v3/depth")
                        .queryParam("symbol", symbol)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(DepthDTO.class);

        if (depth == null) throw new IllegalStateException("Depth nulo");

        // Ordenamos por mejor precio y tomamos los "levels" primeros
        List<List<String>> bids = depth.bids() != null ? depth.bids() : List.of();
        List<List<String>> asks = depth.asks() != null ? depth.asks() : List.of();

        // bids ya vienen ordenadas desc, asks asc según Binance
        double bidVol = 0.0;
        for (int i = 0; i < Math.min(levels, bids.size()); i++) {
            bidVol += safeParseDouble(bids.get(i).get(1));
        }
        double askVol = 0.0;
        for (int i = 0; i < Math.min(levels, asks.size()); i++) {
            askVol += safeParseDouble(asks.get(i).get(1));
        }

        double denom = bidVol + askVol;
        double imbalance = denom == 0.0 ? 0.0 : ((bidVol - askVol) / denom) * 100.0;

        return new OrderbookImbalanceResult(Math.min(levels, Math.min(bids.size(), asks.size())), bidVol, askVol, imbalance);
    }

    /* =================== helpers =================== */

    private static int clampDepthLimit(int levels) {
        // Binance soporta 5, 10, 20, 50, 100, 500, 1000 para depth (según la API)
        int[] opts = {5, 10, 20, 50, 100, 500, 1000};
        int chosen = 5;
        for (int o : opts) {
            if (o >= levels) { chosen = o; break; }
            chosen = o;
        }
        return chosen;
    }

    private static double safeParseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }
}
