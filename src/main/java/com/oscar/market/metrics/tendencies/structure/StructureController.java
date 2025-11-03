package com.oscar.market.metrics.tendencies.structure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/metrics")
public class StructureController {

    private final StructureService service;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public StructureController(StructureService service) {
        this.service = service;
    }

    /** HH/HL vs LH/LL en 1h. window=2 por defecto (fractales de 5 velas). */
    @GetMapping("/hhhl")
    public Map<String, Object> hhhl(
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false, defaultValue = "2") int window
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;

        var r = service.analyze(sym, "1h", window);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", sym);
        out.put("interval", "1h");
        out.put("window", window);
        out.put("highSeq", r.highSeq());
        out.put("lowSeq", r.lowSeq());
        out.put("bias", r.bias());

        out.put("lastHigh", r.lastHigh() == null ? null : Map.of(
                "price", r.lastHigh().price(),
                "time", r.lastHigh().time().toString()
        ));
        out.put("prevHigh", r.prevHigh() == null ? null : Map.of(
                "price", r.prevHigh().price(),
                "time", r.prevHigh().time().toString()
        ));
        out.put("lastLow", r.lastLow() == null ? null : Map.of(
                "price", r.lastLow().price(),
                "time", r.lastLow().time().toString()
        ));
        out.put("prevLow", r.prevLow() == null ? null : Map.of(
                "price", r.prevLow().price(),
                "time", r.prevLow().time().toString()
        ));

        out.put("source", "binance");
        return out;
    }
}
