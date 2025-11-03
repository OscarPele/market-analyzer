package com.oscar.market.metrics.tendencies.structure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.oscar.market.marketdata.BinanceClient;
import com.oscar.market.marketdata.Candle;

@Service
public class StructureService {

    public record SwingPoint(int index, double price, Instant time) {}
    public record StructureResult(String highSeq, String lowSeq, String bias,
                                  SwingPoint lastHigh, SwingPoint prevHigh,
                                  SwingPoint lastLow,  SwingPoint prevLow) {}

    private final BinanceClient client;

    public StructureService(BinanceClient client) {
        this.client = client;
    }

    /** Detecta HH/HL vs LH/LL usando fractales (ventana izquierda/derecha = window). */
    public StructureResult analyze(String symbol, String interval, int window) {
        int limit = Math.max(300, window * 20 + 50);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        int n = candles.size();

        List<Integer> highs = new ArrayList<>();
        List<Integer> lows  = new ArrayList<>();

        for (int i = window; i < n - window; i++) {
            // swing high
            double h = candles.get(i).high();
            boolean isHigh = true;
            for (int j = i - window; j <= i + window; j++) {
                if (j == i) continue;
                if (candles.get(j).high() >= h) { isHigh = false; break; }
            }
            if (isHigh) highs.add(i);

            // swing low
            double l = candles.get(i).low();
            boolean isLow = true;
            for (int j = i - window; j <= i + window; j++) {
                if (j == i) continue;
                if (candles.get(j).low() <= l) { isLow = false; break; }
            }
            if (isLow) lows.add(i);
        }

        SwingPoint lastHigh = null, prevHigh = null, lastLow = null, prevLow = null;

        if (!highs.isEmpty()) {
            int idx = highs.getLast();
            lastHigh = new SwingPoint(idx, candles.get(idx).high(), candles.get(idx).openTime());
        }
        if (highs.size() >= 2) {
            int idx = highs.get(highs.size() - 2);
            prevHigh = new SwingPoint(idx, candles.get(idx).high(), candles.get(idx).openTime());
        }
        if (!lows.isEmpty()) {
            int idx = lows.getLast();
            lastLow = new SwingPoint(idx, candles.get(idx).low(), candles.get(idx).openTime());
        }
        if (lows.size() >= 2) {
            int idx = lows.get(lows.size() - 2);
            prevLow = new SwingPoint(idx, candles.get(idx).low(), candles.get(idx).openTime());
        }

        String highSeq = null;
        if (lastHigh != null && prevHigh != null) {
            highSeq = lastHigh.price() > prevHigh.price() ? "HH" : "LH";
        }
        String lowSeq = null;
        if (lastLow != null && prevLow != null) {
            lowSeq = lastLow.price() > prevLow.price() ? "HL" : "LL";
        }

        String bias = "neutral";
        if ("HH".equals(highSeq) && "HL".equals(lowSeq)) bias = "bullish";
        else if ("LH".equals(highSeq) && "LL".equals(lowSeq)) bias = "bearish";

        return new StructureResult(highSeq, lowSeq, bias, lastHigh, prevHigh, lastLow, prevLow);
    }
}
