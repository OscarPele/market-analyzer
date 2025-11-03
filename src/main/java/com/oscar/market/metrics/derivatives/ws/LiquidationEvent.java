package com.oscar.market.metrics.derivatives.ws;

import jakarta.persistence.*;

@Entity
@Table(name = "liquidation_event",
        indexes = { @Index(name = "idx_liq_symbol_ts", columnList = "symbol,ts") })
public class LiquidationEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;          // ej: BTCUSDT

    @Column(nullable = true, length = 5)
    private String side;            // BUY | SELL (puede venir null)

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private double qty;

    @Column(nullable = false)
    private double notional;        // price * qty

    @Column(nullable = false)
    private long ts;                // epoch ms (o.T)

    protected LiquidationEvent() {}
    public LiquidationEvent(Long id, String symbol, String side,
                            double price, double qty, double notional, long ts) {
        this.id = id; this.symbol = symbol; this.side = side;
        this.price = price; this.qty = qty; this.notional = notional; this.ts = ts;
    }

    // getters
    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public double getPrice() { return price; }
    public double getQty() { return qty; }
    public double getNotional() { return notional; }
    public long getTs() { return ts; }
}
