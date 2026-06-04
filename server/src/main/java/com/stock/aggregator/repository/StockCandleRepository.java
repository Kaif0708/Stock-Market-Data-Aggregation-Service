package com.stock.aggregator.repository;

import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.model.StockCandleKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface StockCandleRepository extends CassandraRepository<StockCandle, StockCandleKey> {

    @Query("SELECT * FROM stock_candles WHERE symbol = ?0 AND datetime >= ?1 AND datetime <= ?2")
    List<StockCandle> findBySymbolAndDateRange(String symbol, Instant startDate, Instant endDate);
}
