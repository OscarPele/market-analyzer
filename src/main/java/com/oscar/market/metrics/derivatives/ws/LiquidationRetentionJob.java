package com.oscar.market.metrics.derivatives.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LiquidationRetentionJob {

    private final LiquidationEventRepository repo;

    @Value("${market.retention.days:7}")
    private int retentionDays;

    public LiquidationRetentionJob(LiquidationEventRepository repo) {
        this.repo = repo;
    }

    // Cada 30 minutos elimina datos antiguos según retención
    @Transactional
    @Scheduled(cron = "0 */30 * * * *")
    public void purgeOld() {
        long now = System.currentTimeMillis();
        long before = now - Math.max(1, retentionDays) * 24L * 60 * 60 * 1000;
        repo.deleteOlderThan(before);
    }
}
