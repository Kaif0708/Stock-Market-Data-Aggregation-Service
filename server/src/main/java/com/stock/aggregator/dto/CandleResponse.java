package com.stock.aggregator.dto;

import java.util.List;

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * This is the WRAPPER for the entire API response.
 * When someone calls GET /api/v1/candles, they get back JSON like this:
 *
 * {
 *   "symbol": "RELIANCE",
 *   "timeframe": "15m",
 *   "candles": [ ... list of CandleDto objects ... ],
 *   "count": 5
 * }
 *
 * This class maps to that JSON structure.
 */
public class CandleResponse {

    private String symbol;            // e.g., "RELIANCE"
    private String timeframe;         // e.g., "15m"
    private List<CandleDto> candles;  // the list of aggregated candles
    private Integer count;            // how many candles in the list


    public CandleResponse() {
    }

    public CandleResponse(String symbol, String timeframe, List<CandleDto> candles, Integer count) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.candles = candles;
        this.count = count;
    }

   
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public List<CandleDto> getCandles() {
        return candles;
    }

    public void setCandles(List<CandleDto> candles) {
        this.candles = candles;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
