package com.oscar.market.metrics.rsi;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metrics")
public class RsiController {

    private final RsiService rsiService;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public RsiController(RsiService rsiService) {
        this.rsiService = rsiService;
    }

    /** RSI(14) en 1h. >50 alcista, <50 bajista. */
    @GetMapping("/rsi14")
    public Map<String, Object> rsi14(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        double value = rsiService.rsi(sym, "1h", 14);
        String bias = value > 50 ? "bullish" : (value < 50 ? "bearish" : "neutral");

        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "period", 14,
                "rsi", value,
                "bias", bias,
                "source", "binance"
        );
    }
}
