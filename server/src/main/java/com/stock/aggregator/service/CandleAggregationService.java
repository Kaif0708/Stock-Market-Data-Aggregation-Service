package com.stock.aggregator.service;

import com.stock.aggregator.dto.CandleDto;
import com.stock.aggregator.exception.InvalidRequestException;
import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.repository.StockCandleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WHAT IS A SERVICE?
 * ------------------
 * A Service class contains the BUSINESS LOGIC of the application.
 * The Controller receives the HTTP request, but the Service does the actual WORK.
 *
 * WHAT DOES THIS SERVICE DO?
 * --------------------------
 * 1. Fetches raw 1-minute candles from the Cassandra database
 * 2. Groups them into bigger time buckets (5m, 15m, 30m, 1h, 1d)
 * 3. Calculates Open, High, Low, Close, Volume for each bucket
 * 4. Returns the aggregated candles
 *
 * EXAMPLE:
 * If user asks for "15m" candles, we take the 1-minute candles
 * from 09:15 to 09:29 and combine them into ONE 15-minute candle.
 *
 * @Service = tells Spring "this is a service class, manage it for me"
 */
@Service
public class CandleAggregationService {

    // Logger - used to print messages to the console (like System.out.println but better)
    private static final Logger log = LoggerFactory.getLogger(CandleAggregationService.class);

    // Spring automatically injects (provides) the repository here
    @Autowired
    private StockCandleRepository stockCandleRepository;

    /**
     * Main method: Fetches candles from DB, groups them by timeframe, and returns aggregated candles.
     *
     * @Cacheable = first time this runs, result is saved in cache.
     *              Next time same parameters are used, result is returned from cache (no DB hit).
     *              The cache key is made from: symbol + timeframe + startDate + endDate
     */
    @Cacheable(value = "aggregatedCandles", key = "{#symbol, #timeframe, #startDate, #endDate}")
    public List<CandleDto> getAggregatedCandles(String symbol, String timeframe, Instant startDate, Instant endDate) {
        log.info("Calculating candle aggregation for symbol: {}, timeframe: {}, range: [{} - {}] (Cache Miss)",
                symbol, timeframe, startDate, endDate);

        long startTime = System.currentTimeMillis();

        // ===== STEP 1: Fetch raw 1-minute candles from the database =====
        List<StockCandle> rawCandles = stockCandleRepository.findBySymbolAndDateRange(symbol, startDate, endDate);

        // If no data found, return empty list
        if (rawCandles.isEmpty()) {
            return Collections.emptyList();
        }

        // ===== STEP 2: Group candles into time buckets =====
        // For example, if timeframe is "15m":
        //   09:15, 09:16, 09:17 ... 09:29 -> all go into the "09:15" bucket
        //   09:30, 09:31, 09:32 ... 09:44 -> all go into the "09:30" bucket
        Map<Instant, List<StockCandle>> bucketMap = new HashMap<>();

        for (StockCandle candle : rawCandles) {
            // Calculate which bucket this candle belongs to
            Instant bucketStart = getBucketStart(candle.getKey().getDatetime(), timeframe);

            // If this bucket doesn't exist yet, create a new list for it
            if (!bucketMap.containsKey(bucketStart)) {
                bucketMap.put(bucketStart, new ArrayList<>());
            }

            // Add this candle to its bucket
            bucketMap.get(bucketStart).add(candle);
        }

        // ===== STEP 3: For each bucket, compute OHLCV (Open, High, Low, Close, Volume) =====
        List<CandleDto> result = new ArrayList<>();

        for (Map.Entry<Instant, List<StockCandle>> entry : bucketMap.entrySet()) {
            Instant bucketStart = entry.getKey();
            List<StockCandle> candlesInBucket = entry.getValue();

            // Sort candles by time (earliest first) so we know which is first and last
            candlesInBucket.sort(Comparator.comparing(c -> c.getKey().getDatetime()));

            // Open  = the OPENING price of the FIRST candle in this bucket
            Double open = candlesInBucket.get(0).getOpen();

            // Close = the CLOSING price of the LAST candle in this bucket
            Double close = candlesInBucket.get(candlesInBucket.size() - 1).getClose();

            // High  = the HIGHEST price across ALL candles in this bucket
            Double high = candlesInBucket.get(0).getHigh();
            for (StockCandle c : candlesInBucket) {
                if (c.getHigh() > high) {
                    high = c.getHigh();
                }
            }

            // Low   = the LOWEST price across ALL candles in this bucket
            Double low = candlesInBucket.get(0).getLow();
            for (StockCandle c : candlesInBucket) {
                if (c.getLow() < low) {
                    low = c.getLow();
                }
            }

            // Volume = the SUM of all volumes in this bucket
            Long volume = 0L;
            for (StockCandle c : candlesInBucket) {
                volume += c.getVolume();
            }

            // Create the aggregated candle DTO and add it to results
            CandleDto aggregatedCandle = new CandleDto(bucketStart, open, high, low, close, volume);
            result.add(aggregatedCandle);
        }

        // ===== STEP 4: Sort the result by datetime (earliest first) =====
        result.sort(Comparator.comparing(CandleDto::getDatetime));

        long duration = System.currentTimeMillis() - startTime;
        log.info("Aggregated {} raw candles into {} '{}' candles in {}ms",
                rawCandles.size(), result.size(), timeframe, duration);

        return result;
    }

    /**
     * Given a datetime and a timeframe, returns the START of the time bucket.
     *
     * EXAMPLE for timeframe "15m":
     *   09:17 -> bucket starts at 09:15   (because 17/15 = 1, so 1*15 = 15)
     *   09:32 -> bucket starts at 09:30   (because 32/15 = 2, so 2*15 = 30)
     *   10:05 -> bucket starts at 10:00   (because 5/15 = 0, so 0*15 = 0)
     *
     * The math: bucketMinute = (currentMinute / interval) * interval
     * This uses integer division to "round down" to the nearest bucket boundary.
     */
    public Instant getBucketStart(Instant datetime, String timeframe) {
        // Convert to ZonedDateTime so we can extract hour, minute, etc.
        ZonedDateTime utcDateTime = datetime.atZone(ZoneOffset.UTC);

        switch (timeframe.toLowerCase()) {
            case "1m":
                // 1-minute timeframe: each candle IS its own bucket, no grouping needed
                return datetime;

            case "5m":
                // Round down minute to nearest multiple of 5
                int m5 = (utcDateTime.getMinute() / 5) * 5;
                return utcDateTime.withMinute(m5).withSecond(0).withNano(0).toInstant();

            case "15m":
                // Round down minute to nearest multiple of 15
                int m15 = (utcDateTime.getMinute() / 15) * 15;
                return utcDateTime.withMinute(m15).withSecond(0).withNano(0).toInstant();

            case "30m":
                // Round down minute to nearest multiple of 30
                int m30 = (utcDateTime.getMinute() / 30) * 30;
                return utcDateTime.withMinute(m30).withSecond(0).withNano(0).toInstant();

            case "1h":
                // Round down to the start of the hour (set minute, second, nano to 0)
                return utcDateTime.withMinute(0).withSecond(0).withNano(0).toInstant();

            case "1d":
                // Round down to the start of the day (midnight UTC)
                return utcDateTime.truncatedTo(ChronoUnit.DAYS).toInstant();

            default:
                throw new InvalidRequestException("Unsupported timeframe: " + timeframe);
        }
    }
}
