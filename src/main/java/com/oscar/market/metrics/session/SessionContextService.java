package com.oscar.market.metrics.session;

import com.oscar.market.marketdata.SpotClient;
import com.oscar.market.marketdata.SpotClient.Kline;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
public class SessionContextService {

    private final SpotClient spot;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    @Value("${market.macro.has-high-impact:false}")
    private boolean macroHasHighImpact;

    @Value("${market.macro.note:}")
    private String macroNote;

    @Value("${market.macro.key-events:}")
    private String macroKeyEventsCsv;

    public SessionContextService(SpotClient spot) {
        this.spot = spot;
    }

    public Map<String, Object> vwapDaily(String symbol) {
        String sym = orDefault(symbol);
        long start = startOfUtcDayMs();
        long now = System.currentTimeMillis();

        var kl = fetch1m(sym, start, now, 1500);
        if (kl.isEmpty() && sym.endsWith("USDC")) kl = fetch1m(toUsdt(sym), start, now, 1500);

        Double vwap = computeVwap(kl);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("vwap", vwap);
        out.put("interval", "1m");
        out.put("window", "today_utc");
        out.put("source", "binance-spot");
        return out;
    }

    public Map<String, Object> avwap(String symbol, Long anchorTs) {
        String sym = orDefault(symbol);
        long anchor = (anchorTs == null) ? startOfUtcDayMs() : anchorTs;
        long now = System.currentTimeMillis();

        var kl = fetch1m(sym, anchor, now, 2000);
        if (kl.isEmpty() && sym.endsWith("USDC")) kl = fetch1m(toUsdt(sym), anchor, now, 2000);

        Double vwap = computeVwap(kl);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("anchorTs", anchor);
        out.put("avwap", vwap);
        out.put("interval", "1m");
        out.put("source", "binance-spot");
        return out;
    }

    public Map<String, Object> prevDayHiLo(String symbol) {
        String sym = orDefault(symbol);
        long todayStart = startOfUtcDayMs();

        // 2 daily velas suelen bastar, pero para robustez pedimos 3
        var dailies = spot.getKlines(sym, "1d", null, null, 3);
        if ((dailies == null || dailies.isEmpty()) && sym.endsWith("USDC")) {
            dailies = spot.getKlines(toUsdt(sym), "1d", null, null, 3);
        }

        Double ph = null, pl = null;
        Long pOpen = null;

        if (dailies != null && !dailies.isEmpty()) {
            Kline prev = null;
            for (Kline k : dailies) {
                if (k.openTime() < todayStart) prev = k;
            }
            if (prev != null) {
                ph = prev.high();
                pl = prev.low();
                pOpen = prev.openTime();
            }
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("prevDayHigh", ph);
        out.put("prevDayLow", pl);
        out.put("prevOpenTime", pOpen);
        out.put("source", "binance-spot");
        return out;
    }

    public Map<String, Object> openingRange60(String symbol) {
        String sym = orDefault(symbol);
        long start = startOfUtcDayMs();
        long end = start + 60L * 60 * 1000;

        var kl = fetch1m(sym, start, end, 1200);
        if (kl.isEmpty() && sym.endsWith("USDC")) kl = fetch1m(toUsdt(sym), start, end, 1200);

        Double hi = null, lo = null;
        if (!kl.isEmpty()) {
            hi = kl.stream().map(Kline::high).max(Double::compare).orElse(null);
            lo = kl.stream().map(Kline::low).min(Double::compare).orElse(null);
        }

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("rangeMinutes", 60);
        out.put("orHigh", hi);
        out.put("orLow", lo);
        out.put("range", hi != null ? hi - lo : null);
        out.put("source", "binance-spot");
        return out;
    }

    public Map<String, Object> sessionsToday() {
        long today = startOfUtcDayMs();

        // Ventanas típicas UTC
        // Asia:   00:00–06:59
        // London: 07:00–12:59
        // NY:     13:00–20:59
        long asiaStart   = today + hours(0);
        long asiaEnd     = today + hours(7) - 1;

        long londonStart = today + hours(7);
        long londonEnd   = today + hours(13) - 1;

        long nyStart     = today + hours(13);
        long nyEnd       = today + hours(21) - 1;

        long now = System.currentTimeMillis();
        String current =
                (in(now, asiaStart, asiaEnd))   ? "ASIA" :
                        (in(now, londonStart, londonEnd)) ? "LONDON" :
                                (in(now, nyStart, nyEnd))         ? "NEW_YORK" : "OFF_HOURS";

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("utcDayStart", today);
        out.put("asia",   Map.of("start", asiaStart,   "end", asiaEnd));
        out.put("london", Map.of("start", londonStart, "end", londonEnd));
        out.put("newYork",Map.of("start", nyStart,     "end", nyEnd));
        out.put("currentSession", current);
        out.put("overlaps", Map.of(
                "asia_london", Map.of("start", londonStart, "end", today + hours(9) - 1),  // 07:00–08:59
                "london_ny",   Map.of("start", nyStart,     "end", today + hours(16) - 1)   // 13:00–15:59
        ));
        out.put("source", "session-static-utc");
        return out;
    }

    public Map<String, Object> macroFlagToday() {
        Map<String,Object> out = new LinkedHashMap<>();
        List<String> events = parseCsv(macroKeyEventsCsv);
        out.put("dateUtc", LocalDate.now(ZoneOffset.UTC).toString());
        out.put("hasHighImpact", macroHasHighImpact);
        out.put("note", (macroNote == null || macroNote.isBlank())
                ? "Requiere proveedor externo (calendario macro) para señal automática."
                : macroNote);
        out.put("keyEvents", events);
        out.put("source", "manual-config");
        return out;
    }

    private List<Kline> fetch1m(String symbol, long start, long end, int limit) {
        return spot.getKlines(symbol, "1m", start, end, limit);
    }

    private static Double computeVwap(List<Kline> kl) {
        if (kl == null || kl.isEmpty()) return null;
        double num = 0.0, den = 0.0;
        for (Kline k : kl) {
            double tp = (k.high() + k.low() + k.close()) / 3.0;
            num += tp * k.volume();
            den += k.volume();
        }
        return den == 0.0 ? null : num / den;
    }

    private static long startOfUtcDayMs() {
        return LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    private static long hours(int h) { return h * 60L * 60 * 1000; }

    private static boolean in(long t, long start, long end) { return t >= start && t <= end; }

    private String orDefault(String s) {
        return (s == null || s.isBlank()) ? defaultSymbol : s;
    }

    private static String toUsdt(String sym) {
        return sym.endsWith("USDC") ? sym.substring(0, sym.length() - 4) + "USDT" : sym;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}
