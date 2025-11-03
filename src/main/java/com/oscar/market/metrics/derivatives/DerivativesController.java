package com.oscar.market.metrics.derivatives;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/derivatives")
public class DerivativesController {

    private final DerivativesService service;

    @Value("${DEFAULT_SYMBOL:BTCUSDC}")
    private String defaultSymbol;

    public DerivativesController(DerivativesService service) {
        this.service = service;
    }

    // 19) OI y ΔOI (1h,4h)
    @GetMapping("/open-interest")
    public Map<String, Object> openInterest(
            @RequestParam(required = false) String symbol
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        return service.openInterestWithDelta(sym);
    }

    // 20) Funding rate (último)
    @GetMapping("/funding-rate")
    public Map<String, Object> fundingRate(
            @RequestParam(required = false) String symbol
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        return service.latestFundingRate(sym);
    }

    // 21) Basis 1M (estandarizado a 30D sobre PERPETUAL)
    @GetMapping("/basis-1m")
    public Map<String, Object> basis1m(
            @RequestParam(required = false) String pair // para /basis es "pair"
    ) {
        String p = (pair == null || pair.isBlank()) ? defaultSymbol : pair;
        return service.basis1M(p);
    }

    // 22) Global Long/Short Account Ratio
    @GetMapping("/long-short-ratio")
    public Map<String, Object> longShortRatio(
            @RequestParam(required = false) String symbol
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        return service.longShortRatio(sym);
    }

    // 23) Liquidaciones últimas 24h
    @GetMapping("/liquidations-24h")
    public Map<String, Object> liquidations24h(
            @RequestParam(required = false) String symbol
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        return service.liquidations24h(sym);
    }

    // 24) Apalancamiento medio estimado (ELR) - placeholder
    @GetMapping("/estimated-leverage")
    public Map<String, Object> estimatedLeverage(
            @RequestParam(required = false) String symbol
    ) {
        String sym = (symbol == null || symbol.isBlank()) ? defaultSymbol : symbol;
        return service.estimatedLeverageRatio(sym);
    }
}
