package com.oscar.market.metrics.flow;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metrics")
public class SpotVolumeFlowController {

    private final SpotVolumeFlowService service;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public SpotVolumeFlowController(SpotVolumeFlowService service) {
        this.service = service;
    }

    /** 14) Volumen vs media 20 (1h) */
    @GetMapping("/volume-ma20")
    public Map<String, Object> volumeMA20(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = service.volumeVsMA20(sym, "1h");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("interval", "1h");
        out.put("lastVolume", r.lastVolume());
        out.put("ma20", r.ma20());
        out.put("ratio", r.ratio());
        out.put("state", r.state()); // high | normal | low
        out.put("source", "binance");
        return out;
    }

    /** 15) OBV pendiente (1h) */
    @GetMapping("/obv-slope")
    public Map<String, Object> obvSlope(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = service.obvSlope(sym, "1h");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("interval", "1h");
        out.put("obv", r.obv());
        out.put("deltaPerBar", r.deltaPerBar());
        out.put("pctPerBar", r.pctPerBar());
        out.put("sign", r.sign()); // up | down | flat
        out.put("source", "binance");
        return out;
    }

    /** 16) CVD (1h) con aggTrades */
    @GetMapping("/cvd1h")
    public Map<String, Object> cvd1h(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = service.flowLastHour(sym);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("window", "1h");
        out.put("buysVolume", r.buysVolume());
        out.put("sellsVolume", r.sellsVolume());
        out.put("cvd", r.cvd()); // buys - sells
        out.put("source", "binance");
        return out;
    }

    /** 17) Buy/Sell Ratio (1h) */
    @GetMapping("/buy-sell-ratio")
    public Map<String, Object> buySellRatio(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = service.flowLastHour(sym);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("window", "1h");
        out.put("buySellRatioPct", r.buySellRatioPct()); // % de volumen agresivo comprador
        out.put("source", "binance");
        return out;
    }

    /** 18) Order book imbalance (top N niveles) */
    @GetMapping("/orderbook-imbalance")
    public Map<String, Object> orderbookImbalance(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "20") int levels
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        var r = service.orderbookImbalance(sym, levels);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("levels", r.levels());
        out.put("bidVolume", r.bidVolume());
        out.put("askVolume", r.askVolume());
        out.put("imbalancePct", r.imbalancePct());
        out.put("source", "binance");
        return out;
    }
}
