package com.oscar.market.metrics.volatility;

import java.util.List;
import java.util.Map;


import org.springframework.stereotype.Service;

import com.oscar.market.marketdata.BinanceClient;
import com.oscar.market.marketdata.Candle;

@Service
public class VolatilityService {

    public record BbWidthResult(double middle, double upper, double lower, double widthAbs, double widthPct) {}

    private final BinanceClient client;

    public VolatilityService(BinanceClient client) {
        this.client = client;
    }

    /** ATR(period) con suavizado de Wilder. Devuelve el último valor. */
    public double atr(String symbol, String interval, int period) {
        int limit = Math.max(period + 100, period + 1);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        return computeAtrWilder(candles, period);
    }

    /** ATR% = ATR / close_actual * 100 */
    public double atrPercent(String symbol, String interval, int period) {
        int limit = Math.max(period + 100, period + 1);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        double atr = computeAtrWilder(candles, period);
        double lastClose = candles.get(candles.size() - 1).close();
        if (lastClose == 0.0) throw new IllegalStateException("Último cierre es 0.");
        return (atr / lastClose) * 100.0;
    }

    /** BB Width (period, kσ). Devuelve middle (SMA), upper, lower, widthAbs y widthPct = (upper-lower)/middle*100. */
    public BbWidthResult bbWidth(String symbol, String interval, int period, double k) {
        int limit = Math.max(period + 50, period + 1);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        if (candles.size() < period) {
            throw new IllegalArgumentException("Datos insuficientes para BB(" + period + ")");
        }
        int n = candles.size();
        double[] window = new double[period];
        for (int i = 0; i < period; i++) {
            window[i] = candles.get(n - period + i).close();
        }
        double mean = mean(window);
        double std  = stdDev(window, mean); // desviación estándar poblacional (N)
        double upper = mean + k * std;
        double lower = mean - k * std;
        double widthAbs = upper - lower;
        double widthPct = mean != 0.0 ? (widthAbs / mean) * 100.0 : 0.0;
        return new BbWidthResult(mean, upper, lower, widthAbs, widthPct);
    }

    /* ----------------- helpers ----------------- */

    private static double computeAtrWilder(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            throw new IllegalArgumentException("Datos insuficientes para ATR(" + period + ")");
        }
        // True Range serie desde i=1 (se necesita close previo)
        int n = candles.size();
        double[] tr = new double[n - 1];
        for (int i = 1; i < n; i++) {
            double high = candles.get(i).high();
            double low  = candles.get(i).low();
            double prevClose = candles.get(i - 1).close();
            double tr1 = high - low;
            double tr2 = Math.abs(high - prevClose);
            double tr3 = Math.abs(low  - prevClose);
            tr[i - 1] = Math.max(tr1, Math.max(tr2, tr3));
        }

        // ATR inicial = media de los primeros 'period' TR
        double atr = 0.0;
        for (int i = 0; i < period; i++) atr += tr[i];
        atr /= period;

        // Suavizado de Wilder
        for (int i = period; i < tr.length; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
        }
        return atr;
    }

    private static double mean(double[] a) {
        double s = 0.0;
        for (double v : a) s += v;
        return s / a.length;
    }

    private static double stdDev(double[] a, double mean) {
        double s2 = 0.0;
        for (double v : a) {
            double d = v - mean;
            s2 += d * d;
        }
        return Math.sqrt(s2 / a.length);
        // Nota: Bollinger usa típicamente la desviación poblacional (N), no muestral (N-1)
    }

    // --- BB–Keltner Squeeze, VWAP distance y Percentil ATR% 30D ---

    public record SqueezeResult(
            Map<String, Object> bb,        // middle, upper, lower, widthAbs, widthPct
            Map<String, Object> kc,        // middle, upper, lower, widthAbs, widthPct
            boolean inSqueeze,             // BB dentro de KC
            String state                   // "squeeze_on" | "squeeze_off" | "neutral"
    ) {}

    public SqueezeResult squeezeBBKeltner(String symbol, String interval,
                                          int bbPeriod, double bbK,
                                          int kcPeriod, double kcMult) {
        // Trae suficiente histórico
        int limit = Math.max(Math.max(bbPeriod, kcPeriod) + 100, 200);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        int n = candles.size();
        if (n < Math.max(bbPeriod, kcPeriod)) {
            throw new IllegalArgumentException("Datos insuficientes para BB/KC");
        }

        // --- Bollinger (sobre cierres)
        double[] bbWindow = new double[bbPeriod];
        for (int i = 0; i < bbPeriod; i++) {
            bbWindow[i] = candles.get(n - bbPeriod + i).close();
        }
        double bbMid = mean(bbWindow);
        double bbStd = stdDev(bbWindow, bbMid);
        double bbUpper = bbMid + bbK * bbStd;
        double bbLower = bbMid - bbK * bbStd;
        double bbWidthAbs = bbUpper - bbLower;
        double bbWidthPct = bbMid != 0 ? (bbWidthAbs / bbMid) * 100.0 : 0.0;

        // --- Keltner (EMA del typical price, banda = kcMult * ATR)
        // typical price = (H+L+C)/3
        List<Double> typical = candles.stream()
                .map(c -> (c.high() + c.low() + c.close()) / 3.0)
                .toList();
        double kcMid = com.oscar.market.metrics.tendencies.ema.EmaService.computeEma(typical, kcPeriod);
        double atr = computeAtrWilder(candles, kcPeriod);
        double kcUpper = kcMid + kcMult * atr;
        double kcLower = kcMid - kcMult * atr;
        double kcWidthAbs = kcUpper - kcLower;
        double kcWidthPct = kcMid != 0 ? (kcWidthAbs / kcMid) * 100.0 : 0.0;

        boolean inSqueeze = (bbUpper <= kcUpper) && (bbLower >= kcLower);
        boolean outside   = (bbUpper >= kcUpper) && (bbLower <= kcLower);
        String state = inSqueeze ? "squeeze_on" : (outside ? "squeeze_off" : "neutral");

        Map<String, Object> bbMap = Map.of(
                "middle", bbMid, "upper", bbUpper, "lower", bbLower,
                "widthAbs", bbWidthAbs, "widthPct", bbWidthPct
        );
        Map<String, Object> kcMap = Map.of(
                "middle", kcMid, "upper", kcUpper, "lower", kcLower,
                "widthAbs", kcWidthAbs, "widthPct", kcWidthPct
        );

        return new SqueezeResult(bbMap, kcMap, inSqueeze, state);
    }

    /** Distancia a VWAP (1h). VWAP calculado con lookback últimas N velas (por defecto 24). */
    public record VwapDistanceResult(double vwap, double close, double distanceAbs, double distancePct) {}
    public VwapDistanceResult vwapDistance(String symbol, String interval, int lookback) {
        int limit = Math.max(lookback + 5, 50);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        if (candles.size() < lookback) {
            throw new IllegalArgumentException("Datos insuficientes para VWAP lookback=" + lookback);
        }
        // Nota: requiere volumen en Candle. Si tu Candle no tiene volume(), añade ese campo.
        double sumPV = 0.0;
        double sumV  = 0.0;
        int start = candles.size() - lookback;
        for (int i = start; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double typical = (c.high() + c.low() + c.close()) / 3.0;
            double v = c.volume(); // <-- asegúrate de que existe este getter
            sumPV += typical * v;
            sumV  += v;
        }
        if (sumV == 0.0) throw new IllegalStateException("Volumen total cero en el lookback VWAP");
        double vwap = sumPV / sumV;
        double close = candles.get(candles.size() - 1).close();
        double distanceAbs = close - vwap;
        double distancePct = vwap != 0 ? (distanceAbs / vwap) * 100.0 : 0.0;
        return new VwapDistanceResult(vwap, close, distanceAbs, distancePct);
    }

    /** Percentil del ATR% (ATR/close*100) en los últimos 'days' días con velas 1h. */
    public record AtrPctPercentileResult(double currentAtrPct, double percentile, int samples) {}
    public AtrPctPercentileResult atrPctPercentile(String symbol, String interval, int period, int days) {
        int hours = Math.max(days * 24, period + 10);
        int limit = Math.max(hours + 100, 800);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        if (candles.size() < period + 2) {
            throw new IllegalArgumentException("Datos insuficientes para ATR%");
        }

        // Construye serie TR y ATR (Wilder) por barra
        int n = candles.size();
        double[] tr = new double[n - 1];
        for (int i = 1; i < n; i++) {
            double high = candles.get(i).high();
            double low  = candles.get(i).low();
            double prevClose = candles.get(i - 1).close();
            double tr1 = high - low;
            double tr2 = Math.abs(high - prevClose);
            double tr3 = Math.abs(low  - prevClose);
            tr[i - 1] = Math.max(tr1, Math.max(tr2, tr3));
        }

        // ATR inicial
        double atr = 0.0;
        for (int i = 0; i < period; i++) atr += tr[i];
        atr /= period;

        // vamos guardando ATR% tras inicialización
        int start = period; // índice en 'tr' donde ya hay ATR válido
        List<Double> atrPctSeries = new java.util.ArrayList<>();
        for (int i = start; i < tr.length; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
            double close = candles.get(i + 1).close(); // tr[i] corresponde a vela i+1
            double atrPct = close != 0 ? (atr / close) * 100.0 : 0.0;
            atrPctSeries.add(atrPct);
        }

        // Toma últimas 'hours' observaciones (hasta 30D) para percentil
        int take = Math.min(hours, atrPctSeries.size());
        if (take < 10) throw new IllegalStateException("Muestras insuficientes para percentil");
        List<Double> window = atrPctSeries.subList(atrPctSeries.size() - take, atrPctSeries.size());
        double current = window.getLast();

        int countLE = 0;
        for (double v : window) if (v <= current) countLE++;
        double percentile = (countLE * 100.0) / window.size();

        return new AtrPctPercentileResult(current, percentile, window.size());
    }

}
