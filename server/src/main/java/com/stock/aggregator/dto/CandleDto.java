package com.stock.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * WHAT IS A DTO?
 * ---------------
 * DTO = Data Transfer Object. It's a simple Java class used to SEND data
 * in the API response (as JSON). It does NOT connect to the database.
 *
 * WHY DO WE NEED THIS?
 * --------------------
 * Our database model (StockCandle) has a composite key structure which is
 * complex. This DTO is a simpler, flat version that's easy to send as JSON.
 *
 * The JSON output for one candle looks like:
 * {
 *   "datetime": "2024-01-15T09:15:00Z",
 *   "open": 2450.5,
 *   "high": 2472.3,
 *   "low": 2448.1,
 *   "close": 2465.8,
 *   "volume": 260300
 * }
 *
 * @JsonFormat = tells Jackson (the JSON library) HOW to format the datetime field
 */
public class CandleDto {

    // The datetime is formatted as "2024-01-15T09:15:00Z" in the JSON response
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant datetime;

    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Long volume;

    // ===== CONSTRUCTORS =====

    public CandleDto() {
    }

    public CandleDto(Instant datetime, Double open, Double high, Double low, Double close, Long volume) {
        this.datetime = datetime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    // ===== GETTERS AND SETTERS =====

    public Instant getDatetime() {
        return datetime;
    }

    public void setDatetime(Instant datetime) {
        this.datetime = datetime;
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
