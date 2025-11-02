package com.oscar.market.metrics.rsi;

import java.util.List;

import org.springframework.stereotype.Service;

import com.oscar.market.marketdata.BinanceClient;
import com.oscar.market.marketdata.Candle;

@Service
public class RsiService {

    private final BinanceClient client;

    public RsiService(BinanceClient client) {
        this.client = client;
    }

    /** RSI de 'period' para símbolo e intervalo dados. */
    public double rsi(String symbol, String interval, int period) {
        int limit = Math.max(period + 100, period + 1);
        List<Candle> candles = client.getKlines(symbol, interval, limit);
        List<Double> closes = candles.stream().map(Candle::close).toList();
        return computeRsi(closes, period);
    }

    /** Cálculo RSI 0–100 con suavizado de Wilder. Devuelve el último valor. */
    public static double computeRsi(List<Double> closes, int period) {
        if (closes.size() < period + 1) {
            throw new IllegalArgumentException("Datos insuficientes para RSI(" + period + ")");
        }

        // ganancias/pérdidas iniciales
        double gainSum = 0.0;
        double lossSum = 0.0;
        for (int i = 1; i <= period; i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff > 0) gainSum += diff;
            else          lossSum += -diff;
        }

        double avgGain = gainSum / period;
        double avgLoss = lossSum / period;

        // suavizado de Wilder sobre el resto
        for (int i = period + 1; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            double gain = diff > 0 ? diff : 0.0;
            double loss = diff < 0 ? -diff : 0.0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0 && avgGain == 0) return 50.0;
        if (avgLoss == 0) return 100.0;
        if (avgGain == 0) return 0.0;

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }
}
