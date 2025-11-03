package com.oscar.market.metrics.derivatives.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Component
public class LiquidationIngestor {

    private final LiquidationEventRepository repo;

    @Value("${market.ws.enabled:true}")
    private boolean enabled;

    @Value("${market.ws.symbols-tracked:BTCUSDT,BTCUSDC}")
    private String symbolsTrackedCsv;

    @Value("${market.ws.min-notional:50}") // USD mínimo para persistir
    private double minNotional;

    @Value("${market.ws.buffer-capacity:20000}")
    private int bufferCapacity;

    @Value("${market.ws.batch-size:200}")
    private int batchSize;

    @Value("${market.ws.flush-interval-ms:1000}")
    private long flushIntervalMs;

    private Set<String> symbolsTracked;
    private final LinkedBlockingQueue<LiquidationEvent> queue = new LinkedBlockingQueue<>();
    private ScheduledExecutorService scheduler;

    public LiquidationIngestor(LiquidationEventRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    void init() {
        if (!enabled) return;
        this.symbolsTracked = Set.of(symbolsTrackedCsv.replace(" ", "").split(","));
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "liq-ingestor-flusher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::flushPeriodic, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            flushAll();
        }
    }

    public void accept(LiquidationEvent e) {
        if (!enabled) return;
        if (e == null) return;
        if (symbolsTracked != null && !symbolsTracked.isEmpty() && !symbolsTracked.contains(e.getSymbol())) {
            return;
        }
        if (e.getNotional() < minNotional) return;

        // intenta encolar; si llena, suelta eventos viejos para no bloquear
        if (!queue.offer(e)) {
            // si no hay capacidad, drenar un lote pequeño y volver a intentar
            drainAndPersist(Math.max(1, batchSize / 2));
            queue.offer(e); // segundo intento (si vuelve a fallar, se descarta silenciosamente)
        }

        if (queue.size() >= batchSize * 2) {
            drainAndPersist(batchSize);
        }
    }

    private void flushPeriodic() {
        drainAndPersist(batchSize);
    }

    private void flushAll() {
        drainAndPersist(Integer.MAX_VALUE);
    }

    private void drainAndPersist(int maxElements) {
        if (queue.isEmpty()) return;
        List<LiquidationEvent> buf = new ArrayList<>(Math.min(maxElements, queue.size()));
        queue.drainTo(buf, maxElements);
        if (buf.isEmpty()) return;

        // batch persist
        repo.saveAll(buf);
        repo.flush();
    }
}
