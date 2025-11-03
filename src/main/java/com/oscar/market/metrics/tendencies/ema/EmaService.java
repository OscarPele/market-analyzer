package com.oscar.market.metrics.tendencies.ema;

import java.util.List;

import org.springframework.stereotype.Service;

import com.oscar.market.marketdata.BinanceClient;
import com.oscar.market.marketdata.Candle;

@Service
public class EmaService {

    private final BinanceClient client;

    public EmaService(BinanceClient client) {
        this.client = client;
    }

    /** EMA genérica para símbolo, intervalo y periodo */
    public double ema(String symbol, String interval, int period) {
        int limit = Math.max(period + 50, period + 1);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        List<Double> closes = candles.stream().map(Candle::close).toList();
        return computeEma(closes, period);
    }

    /** Pendiente de la EMA: delta por vela y porcentaje por vela */
    public EmaSlope emaSlope(String symbol, String interval, int period) {
        int limit = Math.max(period + 50, period + 2); // necesitamos al menos una vela previa
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        List<Double> closes = candles.stream().map(Candle::close).toList();

        if (closes.size() < period + 1) {
            throw new IllegalArgumentException("Datos insuficientes para EMA(" + period + ") en " + interval);
        }

        double emaNow  = computeEma(closes, period);
        double emaPrev = computeEma(closes.subList(0, closes.size() - 1), period);

        double delta = emaNow - emaPrev;
        double pct   = emaPrev != 0.0 ? (delta / emaPrev) * 100.0 : 0.0;
        String sign  = Math.abs(pct) < 0.01 ? "flat" : (pct > 0 ? "up" : "down");

        return new EmaSlope(delta, pct, sign);
    }

    /** Cálculo de EMA dado un array de cierres */
    public static double computeEma(List<Double> data, int period) {
        if (data.size() < period) {
            throw new IllegalArgumentException("Datos insuficientes para EMA(" + period + ")");
        }
        double k = 2.0 / (period + 1);

        // EMA inicial = SMA de los 'period' primeros
        double ema = 0.0;
        for (int i = 0; i < period; i++) {
            ema += data.get(i);
        }
        ema /= period;

        for (int i = period; i < data.size(); i++) {
            double price = data.get(i);
            ema = price * k + ema * (1 - k);
        }
        return ema;
    }

    /** DTO para la pendiente de EMA */
    public static record EmaSlope(double deltaPerBar, double pctPerBar, String sign) {}
}
