package com.oscar.market.marketdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class FuturesClient {

    private final RestClient http;

    public FuturesClient() {
        this.http = RestClient.builder()
                .baseUrl("https://fapi.binance.com")
                .build();
    }

    /* ======== PUBLIC FUTURES DATA ======== */

    // 19) Open Interest hist
    public List<OpenInterestHistItem> getOpenInterestHist(String symbol, String period, int limit) {
        return http.get()
                .uri(u -> u.path("/futures/data/openInterestHist")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("limit", limit)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<OpenInterestHistItem>>() {});
    }

    public static record OpenInterestHistItem(
            String symbol,
            String sumOpenInterest,
            String sumOpenInterestValue,
            long timestamp
    ) {}

    // 20) Funding rate hist
    public List<FundingItem> getFundingRateHistory(String symbol, int limit) {
        return http.get()
                .uri(u -> u.path("/fapi/v1/fundingRate")
                        .queryParam("symbol", symbol)
                        .queryParam("limit", limit)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<FundingItem>>() {});
    }

    public static record FundingItem(String symbol, String fundingRate, long fundingTime) {}

    // 21) Basis
    public List<BasisItem> getBasis(String pair, String contractType, String period, int limit) {
        return http.get()
                .uri(u -> u.path("/futures/data/basis")
                        .queryParam("pair", pair)
                        .queryParam("contractType", contractType)
                        .queryParam("period", period)
                        .queryParam("limit", limit)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<BasisItem>>() {});
    }

    public static record BasisItem(
            String pair, String contractType, long timestamp,
            double basis, double basisRate, Double annualizedBasisRate,
            double futuresPrice, double indexPrice
    ) {}

    // 22) Global Long/Short
    public List<LongShortItem> getGlobalLongShortAccountRatio(String symbol, String period, int limit) {
        return http.get()
                .uri(u -> u.path("/futures/data/globalLongShortAccountRatio")
                        .queryParam("symbol", symbol)
                        .queryParam("period", period)
                        .queryParam("limit", limit)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LongShortItem>>() {});
    }

    public static record LongShortItem(
            String symbol, String longShortRatio, String longAccount, String shortAccount, long timestamp
    ) {}

    /* 23) Liquidaciones: ventana simple */
    public List<Map<String, Object>> getLiquidationsWindow(
            String symbol, long startTime, long endTime, Integer limit, String autoCloseType) {
        int lim = Math.min(Math.max(limit == null ? 1000 : limit, 1), 1000);
        try {
            return http.get()
                    .uri(u -> {
                        var b = u.path("/futures/data/liquidationOrders")
                                .queryParam("startTime", startTime)
                                .queryParam("endTime", endTime)
                                .queryParam("limit", lim);
                        if (symbol != null && !symbol.isBlank()) b.queryParam("symbol", symbol);
                        if (autoCloseType != null && !autoCloseType.isBlank())
                            b.queryParam("autoCloseType", autoCloseType); // LIQUIDATION | ADL
                        return b.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        } catch (HttpClientErrorException e) {
            return List.of();
        }
    }

    /* 23) Liquidaciones: paginado por ventanas */
    public List<Map<String, Object>> getLiquidationsPaged(
            String symbol, long startTime, long endTime, int windowMinutes, String autoCloseType) {
        long step = windowMinutes * 60L * 1000L;
        List<Map<String, Object>> out = new ArrayList<>();
        for (long from = startTime; from < endTime; from += step) {
            long to = Math.min(from + step - 1, endTime);
            out.addAll(getLiquidationsWindow(symbol, from, to, 1000, autoCloseType));
        }
        return out;
    }

    public static long nowMs() { return Instant.now().toEpochMilli(); }
}
