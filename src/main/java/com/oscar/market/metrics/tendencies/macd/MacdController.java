package com.oscar.market.metrics.tendencies.macd;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metrics")
public class MacdController {

    private final MacdService macdService;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public MacdController(MacdService macdService) {
        this.macdService = macdService;
    }

    /** MACD histograma en 1h. >0 positivo. */
    @GetMapping("/macd-histogram")
    public Map<String, Object> macdHistogram(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "12") int fast,
            @RequestParam(required = false, defaultValue = "26") int slow,
            @RequestParam(required = false, defaultValue = "9") int signal
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;

        var r = macdService.macdHistogram(sym, "1h", fast, slow, signal);

        return Map.of(
                "symbol", sym,
                "interval", "1h",
                "fast", fast,
                "slow", slow,
                "signal", signal,
                "macd", r.macd(),
                "signalValue", r.signal(),
                "histogram", r.histogram(),
                "sign", r.sign(),   // positive | negative | flat
                "source", "binance"
        );
    }
}
