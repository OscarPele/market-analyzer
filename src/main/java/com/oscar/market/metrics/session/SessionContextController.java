package com.oscar.market.metrics.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/session")
public class SessionContextController {

    private final SessionContextService service;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public SessionContextController(SessionContextService service) {
        this.service = service;
    }

    @GetMapping("/vwap-daily")
    public Map<String, Object> vwapDaily(@RequestParam(required = false) String symbol) {
        return service.vwapDaily(symbol);
    }

    @GetMapping("/avwap")
    public Map<String, Object> avwap(@RequestParam(required = false) String symbol,
                                     @RequestParam(required = false) Long anchorTs) {
        return service.avwap(symbol, anchorTs);
    }

    @GetMapping("/prev-day-hilo")
    public Map<String, Object> prevDayHiLo(@RequestParam(required = false) String symbol) {
        return service.prevDayHiLo(symbol);
    }

    @GetMapping("/opening-range-60")
    public Map<String, Object> openingRange60(@RequestParam(required = false) String symbol) {
        return service.openingRange60(symbol);
    }

    @GetMapping("/sessions")
    public Map<String, Object> sessions() {
        return service.sessionsToday();
    }

    @GetMapping("/macro-flag")
    public Map<String, Object> macroFlag() {
        return service.macroFlagToday();
    }
}
