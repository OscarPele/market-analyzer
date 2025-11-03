package com.oscar.market.metrics.derivatives;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.oscar.market.metrics.derivatives.ws.LiquidationEventRepository;
import com.oscar.market.marketdata.FuturesClient;
import com.oscar.market.marketdata.FuturesClient.BasisItem;
import com.oscar.market.marketdata.FuturesClient.FundingItem;
import com.oscar.market.marketdata.FuturesClient.LongShortItem;
import com.oscar.market.marketdata.FuturesClient.OpenInterestHistItem;

@Service
public class DerivativesService {

    private final FuturesClient futures;
    private final LiquidationEventRepository liqRepo;

    public DerivativesService(FuturesClient futures, LiquidationEventRepository liqRepo) {
        this.futures = futures;
        this.liqRepo = liqRepo;
    }

    /* 19) OI + ΔOI */
    public Map<String, Object> openInterestWithDelta(String symbol) {
        Map<String, Object> out = new LinkedHashMap<>();
        var oi1h = futures.getOpenInterestHist(symbol, "1h", 2);
        var oi4h = futures.getOpenInterestHist(symbol, "4h", 2);
        out.put("symbol", symbol);
        out.put("oi", Map.of(
                "1h", computeOiDelta(oi1h),
                "4h", computeOiDelta(oi4h)
        ));
        out.put("source", "binance-futures");
        return out;
    }

    private Map<String, Object> computeOiDelta(List<OpenInterestHistItem> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (items == null || items.isEmpty()) {
            m.put("openInterest", null);
            m.put("openInterestValue", null);
            m.put("delta", null);
            m.put("pct", null);
            m.put("timestamp", null);
            return m;
        }
        var last = items.getLast();
        double curOi = parseD(last.sumOpenInterest());
        double curVal = parseD(last.sumOpenInterestValue());
        Double delta = null, pct = null;
        if (items.size() >= 2) {
            var prev = items.get(items.size() - 2);
            double prevOi = parseD(prev.sumOpenInterest());
            delta = curOi - prevOi;
            if (prevOi != 0) pct = (delta / prevOi) * 100.0;
        }
        m.put("openInterest", curOi);
        m.put("openInterestValue", curVal);
        m.put("delta", delta);
        m.put("pct", pct);
        m.put("timestamp", last.timestamp());
        return m;
    }

    /* 20) Funding último */
    public Map<String, Object> latestFundingRate(String symbol) {
        Map<String, Object> out = new LinkedHashMap<>();
        var list = futures.getFundingRateHistory(symbol, 1);
        if (list == null || list.isEmpty()) {
            out.put("symbol", symbol);
            out.put("fundingRate", null);
            out.put("fundingTime", null);
            out.put("fundingRatePct", null);
            out.put("source", "binance-futures");
            return out;
        }
        FundingItem it = list.getFirst();
        double rate = parseD(it.fundingRate());
        out.put("symbol", symbol);
        out.put("fundingRate", rate);
        out.put("fundingTime", it.fundingTime());
        out.put("fundingRatePct", rate * 100.0);
        out.put("source", "binance-futures");
        return out;
    }

    /* 21) Basis 1M */
    public Map<String, Object> basis1M(String pair) {
        Map<String, Object> out = new LinkedHashMap<>();
        var list = futures.getBasis(pair, "PERPETUAL", "1h", 1);
        if (list == null || list.isEmpty()) {
            out.put("pair", pair);
            out.put("basisRate", null);
            out.put("annualized1M", null);
            out.put("futuresPrice", null);
            out.put("indexPrice", null);
            out.put("contractType", "PERPETUAL");
            out.put("source", "binance-futures");
            return out;
        }
        BasisItem it = list.getFirst();
        double basisRate = it.basisRate();
        double annualized1M = basisRate * (365.0 / 30.0);
        out.put("pair", pair);
        out.put("contractType", it.contractType());
        out.put("basis", it.basis());
        out.put("basisRate", basisRate);
        out.put("annualized1M", annualized1M);
        out.put("futuresPrice", it.futuresPrice());
        out.put("indexPrice", it.indexPrice());
        out.put("source", "binance-futures");
        return out;
    }

    /* 22) Long/Short ratio */
    public Map<String, Object> longShortRatio(String symbol) {
        Map<String, Object> out = new LinkedHashMap<>();
        var list = futures.getGlobalLongShortAccountRatio(symbol, "1h", 1);
        if (list == null || list.isEmpty()) {
            out.put("symbol", symbol);
            out.put("interval", "1h");
            out.put("longShortRatio", null);
            out.put("longAccount", null);
            out.put("shortAccount", null);
            out.put("timestamp", null);
            out.put("source", "binance-futures");
            return out;
        }
        LongShortItem it = list.getFirst();
        out.put("symbol", symbol);
        out.put("interval", "1h");
        out.put("longShortRatio", parseD(it.longShortRatio()));
        out.put("longAccount", parseD(it.longAccount()));
        out.put("shortAccount", parseD(it.shortAccount()));
        out.put("timestamp", it.timestamp());
        out.put("source", "binance-futures");
        return out;
    }

    /* 23) Liquidaciones 24h: SOLO BD (WS). Si no hay datos, avisar. */
    public Map<String, Object> liquidations24h(String symbol) {
        Map<String, Object> out = new LinkedHashMap<>();
        long end = FuturesClient.nowMs();
        long start = end - 24L * 60 * 60 * 1000;

        String queried = symbol;
        String note = null;

        long count = liqRepo.countBySymbolAndTsGreaterThanEqual(symbol, start);

        // Fallback a USDT solo dentro de BD, por si el WS guarda USDT
        if (count == 0 && symbol.endsWith("USDC")) {
            String alt = symbol.substring(0, symbol.length() - 4) + "USDT";
            long altCount = liqRepo.countBySymbolAndTsGreaterThanEqual(alt, start);
            if (altCount > 0) {
                queried = alt;
                count = altCount;
                note = "source=ws-db; fallback a USDT";
            }
        }

        long buys = 0, sells = 0;
        double notional = 0.0;

        if (count > 0) {
            buys  = liqRepo.countBySymbolAndSideIgnoreCaseAndTsGreaterThanEqual(queried, "BUY", start);
            sells = liqRepo.countBySymbolAndSideIgnoreCaseAndTsGreaterThanEqual(queried, "SELL", start);
            notional = liqRepo.sumNotionalSince(queried, start);
            if (note == null) note = "source=ws-db";
        } else {
            note = "sin datos persistidos aún (WS)";
        }

        out.put("symbol", symbol);
        out.put("symbolQueried", queried);
        out.put("window", "24h");
        out.put("count", count);
        out.put("totalNotional", notional);
        out.put("bySide", Map.of("BUY", buys, "SELL", sells));
        out.put("note", note);
        out.put("source", "binance-futures");
        return out;
    }

    /* 24) ELR placeholder */
    public Map<String, Object> estimatedLeverageRatio(String symbol) {
        Map<String, Object> out = new LinkedHashMap<>();
        var oi = futures.getOpenInterestHist(symbol, "1h", 1);
        Double oiUsd = null; Long ts = null;
        if (oi != null && !oi.isEmpty()) {
            var it = oi.getFirst();
            oiUsd = parseD(it.sumOpenInterestValue());
            ts = it.timestamp();
        }
        out.put("symbol", symbol);
        out.put("elr", null);
        out.put("oiUsd", oiUsd);
        out.put("timestamp", ts);
        out.put("note", "ELR requiere 'exchange reserves' externas. Integrar proveedor on-chain.");
        out.put("source", "binance-futures + external required");
        return out;
    }

    /* utils */
    private static double parseD(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
    }
}
