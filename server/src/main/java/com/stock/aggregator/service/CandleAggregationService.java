package com.stock.aggregator.service;

import com.stock.aggregator.dto.CandleDto;
import com.stock.aggregator.exception.InvalidRequestException;
import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.repository.StockCandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleAggregationService {

    private final StockCandleRepository stockCandleRepository;

    /**
     * Retrieves and aggregates stock candles.
     * Cached based on symbol, timeframe, and date ranges.
     */
    @Cacheable(value = "aggregatedCandles", key = "{#symbol, #timeframe, #startDate, #endDate}")
    public List<CandleDto> getAggregatedCandles(String symbol, String timeframe, Instant startDate, Instant endDate) {
        log.info("Calculating candle aggregation for symbol: {}, timeframe: {}, range: [{} - {}] (Cache Miss)",
                symbol, timeframe, startDate, endDate);

        long startTime = System.currentTimeMillis();

        // 1. Fetch raw candles from Cassandra (sorted DESC by Clustering Key)
        List<StockCandle> rawCandles = stockCandleRepository.findBySymbolAndDateRange(symbol, startDate, endDate);
        if (rawCandles.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Align and Group by the requested timeframe bucket
        Map<Instant, List<StockCandle>> grouped = rawCandles.stream()
                .collect(Collectors.groupingBy(c -> getBucketStart(c.getKey().getDatetime(), timeframe)));

        // 3. Compute OHLCV for each bucket in chronological order
        List<CandleDto> result = grouped.entrySet().stream()
                .map(entry -> {
                    Instant bucketStart = entry.getKey();
                    List<StockCandle> candlesInBucket = entry.getValue();

                    // Sort chronologically to correctly identify 'open' (first) and 'close' (last)
                    candlesInBucket.sort(Comparator.comparing(c -> c.getKey().getDatetime()));

                    Double open = candlesInBucket.get(0).getOpen();
                    Double close = candlesInBucket.get(candlesInBucket.size() - 1).getClose();
                    Double high = candlesInBucket.stream().mapToDouble(StockCandle::getHigh).max().orElse(0.0);
                    Double low = candlesInBucket.stream().mapToDouble(StockCandle::getLow).min().orElse(0.0);
                    Long volume = candlesInBucket.stream().mapToLong(StockCandle::getVolume).sum();

                    return CandleDto.builder()
                            .datetime(bucketStart)
                            .open(open)
                            .high(high)
                            .low(low)
                            .close(close)
                            .volume(volume)
                            .build();
                })
                .sorted(Comparator.comparing(CandleDto::getDatetime))
                .collect(Collectors.toList());

        long duration = System.currentTimeMillis() - startTime;
        log.info("Aggregated {} raw candles into {} '{}' candles in {}ms",
                rawCandles.size(), result.size(), timeframe, duration);

        return result;
    }

    /**
     * Helper to compute the timeframe bucket starting Instant for a given datetime.
     */
    public Instant getBucketStart(Instant datetime, String timeframe) {
        ZonedDateTime utcDateTime = datetime.atZone(ZoneOffset.UTC);
        switch (timeframe.toLowerCase()) {
            case "1m":
                return datetime;
            case "5m":
                int m5 = (utcDateTime.getMinute() / 5) * 5;
                return utcDateTime.withMinute(m5).withSecond(0).withNano(0).toInstant();
            case "15m":
                int m15 = (utcDateTime.getMinute() / 15) * 15;
                return utcDateTime.withMinute(m15).withSecond(0).withNano(0).toInstant();
            case "30m":
                int m30 = (utcDateTime.getMinute() / 30) * 30;
                return utcDateTime.withMinute(m30).withSecond(0).withNano(0).toInstant();
            case "1h":
                return utcDateTime.withMinute(0).withSecond(0).withNano(0).toInstant();
            case "1d":
                return utcDateTime.truncatedTo(ChronoUnit.DAYS).toInstant();
            default:
                throw new InvalidRequestException("Unsupported timeframe: " + timeframe);
        }
    }
}
