package com.oscar.market.marketdata;

import java.time.Instant;

public record Candle(
        Instant openTime,
        double open,
        double high,
        double low,
        double close,
        double volume,
        Instant closeTime
) { }
