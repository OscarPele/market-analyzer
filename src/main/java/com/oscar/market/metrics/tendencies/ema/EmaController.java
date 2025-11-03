package com.oscar.market.metrics.tendencies.ema;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
public class EmaController {

    private final EmaService emaService;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public EmaController(EmaService emaService) {
        this.emaService = emaService;
    }

    @GetMapping("/ema200")
    public Map<String, Object> ema200(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        double ema1h = emaService.ema(sym, "1h", 200);
        double ema4h = emaService.ema(sym, "4h", 200);
        return Map.of(
                "symbol", sym,
                "ema200", Map.of("1h", ema1h, "4h", ema4h),
                "source", "binance"
        );
    }

    @GetMapping("/ema50")
    public Map<String, Object> ema50(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        double ema1h = emaService.ema(sym, "1h", 50);
        return Map.of(
                "symbol", sym,
                "ema50", Map.of("1h", ema1h),
                "source", "binance"
        );
    }

    @GetMapping("/ema21")
    public Map<String, Object> ema21(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        double ema1h = emaService.ema(sym, "1h", 21);
        return Map.of(
                "symbol", sym,
                "ema21", Map.of("1h", ema1h),
                "source", "binance"
        );
    }

    @GetMapping("/ema200-slope")
    public Map<String, Object> ema200Slope(@RequestParam(required = false) String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;

        EmaService.EmaSlope oneH  = emaService.emaSlope(sym, "1h", 200);
        EmaService.EmaSlope fourH = emaService.emaSlope(sym, "4h", 200);

        return Map.of(
                "symbol", sym,
                "ema200_slope", Map.of(
                        "1h", oneH,
                        "4h", fourH
                ),
                "source", "binance"
        );
    }
}
