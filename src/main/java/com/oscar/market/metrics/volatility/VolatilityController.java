package com.oscar.market.metrics.volatility;

import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metrics")
public class VolatilityController {

    private final VolatilityService volService;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public VolatilityController(VolatilityService volService) {
        this.volService = volService;
    }

    /** 8) ATR 14 (1h) */
    @GetMapping("/atr14")
    public Map<String, Object> atr14(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        double atr = volService.atr(sym, "1h", 14);
        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "period", 14,
                "atr", atr,
                "source", "binance"
        );
    }

    /** 9) ATR% (ATR/close*100) con periodo 14 (1h) */
    @GetMapping("/atr14pct")
    public Map<String, Object> atr14pct(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        double atrPct = volService.atrPercent(sym, "1h", 14);
        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "period", 14,
                "atrPct", atrPct,
                "source", "binance"
        );
    }

    /** 10) BB Width 20, 2σ (1h). Devuelve ancho absoluto y % respecto a la media. */
    @GetMapping("/bbwidth")
    public Map<String, Object> bbwidth(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "20") int period,
            @RequestParam(required = false, defaultValue = "2.0") double k
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = volService.bbWidth(sym, "1h", period, k);

        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "period", period,
                "k", k,
                "middle", r.middle(),
                "upper", r.upper(),
                "lower", r.lower(),
                "widthAbs", r.widthAbs(),
                "widthPct", r.widthPct(),
                "source", "binance"
        );
    }

    /** 11) Squeeze BB–Keltner (1h). BB dentro de Keltner = compresión */
    @GetMapping("/squeeze")
    public Map<String, Object> squeeze(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "20") int bbPeriod,
            @RequestParam(required = false, defaultValue = "2.0") double bbK,
            @RequestParam(required = false, defaultValue = "20") int kcPeriod,
            @RequestParam(required = false, defaultValue = "1.5") double kcMult
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = volService.squeezeBBKeltner(sym, "1h", bbPeriod, bbK, kcPeriod, kcMult);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("interval", "1h");
        out.put("bbPeriod", bbPeriod);
        out.put("bbK", bbK);
        out.put("kcPeriod", kcPeriod);
        out.put("kcMult", kcMult);
        out.put("bb", r.bb());
        out.put("kc", r.kc());
        out.put("inSqueeze", r.inSqueeze());
        out.put("state", r.state());
        out.put("source", "binance");
        return out;
    }

    /** 12) Distancia a VWAP (1h). lookback=24 por defecto */
    @GetMapping("/vwap-distance")
    public Map<String, Object> vwapDistance(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "24") int lookback
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = volService.vwapDistance(sym, "1h", lookback);
        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "lookback", lookback,
                "vwap", r.vwap(),
                "close", r.close(),
                "distanceAbs", r.distanceAbs(),
                "distancePct", r.distancePct(),
                "source", "binance"
        );
    }

    /** 13) Percentil ATR% en 30 días (1h). period=14 por defecto */
    @GetMapping("/atrpct-percentile")
    public Map<String, Object> atrPctPercentile(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "14") int period,
            @RequestParam(required = false, defaultValue = "30") int days
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = volService.atrPctPercentile(sym, "1h", period, days);
        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "period", period,
                "days", days,
                "currentAtrPct", r.currentAtrPct(),
                "percentile", r.percentile(),  // 0–100
                "samples", r.samples(),
                "source", "binance"
        );
    }

}
