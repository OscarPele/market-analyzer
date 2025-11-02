package com.oscar.market.marketdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class BinanceClient {

    private final RestTemplate rt;

    public BinanceClient(RestTemplate rt) {
        this.rt = rt;
    }

    public List<Candle> getKlines(String symbol, String interval, int limit) {
        String url = "https://api.binance.com/api/v3/klines?symbol="
                + symbol + "&interval=" + interval + "&limit=" + limit;

        @SuppressWarnings("rawtypes")
        ResponseEntity<List> resp = rt.getForEntity(url, List.class);
        @SuppressWarnings("unchecked")
        List<List<Object>> raw = resp.getBody();

        List<Candle> out = new ArrayList<>();
        if (raw == null) return out;

        for (List<Object> a : raw) {
            long openMs = ((Number) a.get(0)).longValue();
            double open = Double.parseDouble(a.get(1).toString());
            double high = Double.parseDouble(a.get(2).toString());
            double low  = Double.parseDouble(a.get(3).toString());
            double close= Double.parseDouble(a.get(4).toString());
            double vol  = Double.parseDouble(a.get(5).toString());
            long closeMs= ((Number) a.get(6)).longValue();

            out.add(new Candle(
                    Instant.ofEpochMilli(openMs),
                    open, high, low, close, vol,
                    Instant.ofEpochMilli(closeMs)
            ));
        }
        return out;
    }
}
