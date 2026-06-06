package com.stock.aggregator.repository;

import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.model.StockCandleKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * WHAT IS A REPOSITORY?
 * ---------------------
 * A Repository is the layer that talks to the DATABASE.
 * It provides methods to read/write data from/to Cassandra.
 *
 * HOW DOES IT WORK?
 * -----------------
 * We extend CassandraRepository which gives us ready-made methods like:
 *   - save(entity)      -> INSERT a row
 *   - findById(key)     -> SELECT by primary key
 *   - findAll()         -> SELECT all rows
 *   - delete(entity)    -> DELETE a row
 *
 * We also define our OWN custom query using @Query annotation.
 *
 * CassandraRepository<StockCandle, StockCandleKey> means:
 *   - StockCandle    = the entity (Java class) this repository manages
 *   - StockCandleKey = the type of the primary key
 *
 * @Repository = tells Spring "this is a database access class, manage it for me"
 */
@Repository
public interface StockCandleRepository extends CassandraRepository<StockCandle, StockCandleKey> {

    /**
     * Custom CQL (Cassandra Query Language) query:
     * "Give me all stock candles WHERE the symbol matches AND the datetime is
     *  between startDate and endDate"
     *
     * ?0 = first parameter (symbol)
     * ?1 = second parameter (startDate)
     * ?2 = third parameter (endDate)
     *
     * This is like SQL but for Cassandra!
     */
    @Query("SELECT * FROM stock_candles WHERE symbol = ?0 AND datetime >= ?1 AND datetime <= ?2")
    List<StockCandle> findBySymbolAndDateRange(String symbol, Instant startDate, Instant endDate);
}
