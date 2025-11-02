package com.oscar.market.metrics.macd;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.oscar.market.marketdata.BinanceClient;
import com.oscar.market.marketdata.Candle;

@Service
public class MacdService {

    public record MacdResult(double macd, double signal, double histogram, String sign) {}

    private final BinanceClient client;

    public MacdService(BinanceClient client) {
        this.client = client;
    }

    /** MACD estándar: fast=12, slow=26, signal=9 */
    public MacdResult macdHistogram(String symbol, String interval, int fast, int slow, int signal) {
        if (fast <= 0 || slow <= 0 || signal <= 0 || fast >= slow) {
            throw new IllegalArgumentException("Parámetros MACD inválidos");
        }

        // Trae suficiente histórico para estabilizar EMAs
        int limit = Math.max(slow + signal + 200, 300);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        List<Double> closes = candles.stream().map(Candle::close).toList();
        int n = closes.size();
        if (n < slow + signal) {
            throw new IllegalStateException("Datos insuficientes para MACD");
        }

        // EMA fast y slow serie completa
        Double[] emaFast = emaSeries(closes, fast);
        Double[] emaSlow = emaSeries(closes, slow);

        // Serie MACD = EMAfast - EMAslow
        Double[] macdSeries = new Double[n];
        for (int i = 0; i < n; i++) {
            if (emaFast[i] != null && emaSlow[i] != null) {
                macdSeries[i] = emaFast[i] - emaSlow[i];
            }
        }

        // Construye la serie compacta de MACD (sin nulls) para calcular la signal EMA
        List<Double> macdCompact = new ArrayList<>();
        for (Double v : macdSeries) if (v != null) macdCompact.add(v);
        if (macdCompact.size() < signal) {
            throw new IllegalStateException("Datos insuficientes para signal");
        }

        double signalLast = emaLast(macdCompact, signal);

        // Último MACD (último no-null)
        double macdLast = macdCompact.getLast();
        double hist = macdLast - signalLast;
        String signTxt = Math.abs(hist) < 1e-9 ? "flat" : (hist > 0 ? "positive" : "negative");

        return new MacdResult(macdLast, signalLast, hist, signTxt);
    }

    /** Serie EMA con null hasta period-1; desde ahí EMA clásica. */
    private static Double[] emaSeries(List<Double> data, int period) {
        int n = data.size();
        Double[] out = new Double[n];
        if (n < period) return out;

        double k = 2.0 / (period + 1);
        double sma = 0.0;
        for (int i = 0; i < period; i++) sma += data.get(i);
        sma /= period;
        out[period - 1] = sma;

        for (int i = period; i < n; i++) {
            double price = data.get(i);
            out[i] = price * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Último valor de EMA sobre una lista compacta (sin nulls). */
    private static double emaLast(List<Double> data, int period) {
        if (data.size() < period) throw new IllegalArgumentException("Datos insuficientes");
        double k = 2.0 / (period + 1);
        double ema = 0.0;
        for (int i = 0; i < period; i++) ema += data.get(i);
        ema /= period;
        for (int i = period; i < data.size(); i++) {
            double price = data.get(i);
            ema = price * k + ema * (1 - k);
        }
        return ema;
    }
}
