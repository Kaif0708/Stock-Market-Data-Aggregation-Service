package com.stock.aggregator.model;

import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * This class represents ONE ROW in the "stock_candles" Cassandra table.
 * Each row = one 1-minute stock candle (a candlestick in stock charts).
 *
 * WHAT IS A CANDLE?
 * -----------------
 * A candle summarizes stock price movement in a time period:
 *   - open   = price at the START of the minute
 *   - high   = HIGHEST price during the minute
 *   - low    = LOWEST price during the minute
 *   - close  = price at the END of the minute
 *   - volume = total number of shares traded during the minute
 *
 * @Table("stock_candles") = maps this Java class to the "stock_candles" table in Cassandra
 * @PrimaryKey             = marks the 'key' field as the table's primary key
 * @Column("column_name")  = maps each Java field to a Cassandra column
 */
@Table("stock_candles")
public class StockCandle {

    @PrimaryKey
    private StockCandleKey key;    // composite key = symbol + datetime

    @Column("open")
    private Double open;           // opening price

    @Column("high")
    private Double high;           // highest price

    @Column("low")
    private Double low;            // lowest price

    @Column("close")
    private Double close;          // closing price

    @Column("volume")
    private Long volume;           // number of shares traded

    // ===== CONSTRUCTORS =====

    // No-argument constructor (required by Spring Data)
    public StockCandle() {
    }

    // Constructor with all fields
    public StockCandle(StockCandleKey key, Double open, Double high, Double low, Double close, Long volume) {
        this.key = key;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // ===== GETTERS AND SETTERS =====

    public StockCandleKey getKey() {
        return key;
    }

    public void setKey(StockCandleKey key) {
        this.key = key;
    }

    public Double getOpen() {
        return open;
    }

    public void setOpen(Double open) {
        this.open = open;
    }

    public Double getHigh() {
        return high;
    }

    public void setHigh(Double high) {
        this.high = high;
    }

    public Double getLow() {
        return low;
    }

    public void setLow(Double low) {
        this.low = low;
    }

    public Double getClose() {
        return close;
    }

    public void setClose(Double close) {
        this.close = close;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }
}
