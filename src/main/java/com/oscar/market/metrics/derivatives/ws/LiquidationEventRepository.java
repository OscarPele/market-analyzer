package com.oscar.market.metrics.derivatives.ws;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiquidationEventRepository extends JpaRepository<LiquidationEvent, Long> {
    long countBySymbolAndTsGreaterThanEqual(String symbol, long since);
    long countBySymbolAndSideIgnoreCaseAndTsGreaterThanEqual(String symbol, String side, long since);

    @Query("select coalesce(sum(e.notional),0) from LiquidationEvent e " +
            "where e.symbol = :symbol and e.ts >= :since")
    double sumNotionalSince(@Param("symbol") String symbol, @Param("since") long since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LiquidationEvent e where e.ts < :beforeTs")
    int deleteOlderThan(@Param("beforeTs") long beforeTs);
}
