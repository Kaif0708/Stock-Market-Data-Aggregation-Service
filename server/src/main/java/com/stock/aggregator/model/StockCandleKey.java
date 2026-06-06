package com.stock.aggregator.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * In Cassandra, the Primary Key can have MULTIPLE columns.
 * This class represents the COMPOSITE PRIMARY KEY of the "stock_candles" table.
 *
 * Our table's primary key has 2 parts:
 *   1. symbol   -> PARTITION KEY  (decides WHICH node stores the data)
 *   2. datetime -> CLUSTERING KEY (decides the ORDER of rows within a partition)
 *
 * Think of it like a filing cabinet:
 *   - symbol   = which drawer (e.g., "RELIANCE" drawer, "TCS" drawer)
 *   - datetime = how files are sorted inside that drawer (newest first = DESC)
 *
 * @PrimaryKeyClass = tells Spring Data Cassandra "this class IS a composite primary key"
 * Serializable     = needed because primary keys must be serializable (convertible to bytes)
 */
@PrimaryKeyClass
public class StockCandleKey implements Serializable {

    // PARTITION KEY: all rows with the same symbol go to the same Cassandra node
    @PrimaryKeyColumn(name = "symbol", type = PrimaryKeyType.PARTITIONED)
    private String symbol;

    // CLUSTERING KEY: within a partition, rows are sorted by datetime in DESCENDING order
    @PrimaryKeyColumn(name = "datetime", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant datetime;

    // ===== CONSTRUCTORS =====

    // No-argument constructor (required by Spring Data)
    public StockCandleKey() {
    }

    // Constructor with all fields (convenient for creating keys)
    public StockCandleKey(String symbol, Instant datetime) {
        this.symbol = symbol;
        this.datetime = datetime;
    }

    // ===== GETTERS AND SETTERS =====

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Instant getDatetime() {
        return datetime;
    }

    public void setDatetime(Instant datetime) {
        this.datetime = datetime;
    }
}
