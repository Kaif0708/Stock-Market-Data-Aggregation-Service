package com.stock.aggregator.service;

import com.stock.aggregator.dto.CandleDto;
import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.model.StockCandleKey;
import com.stock.aggregator.repository.StockCandleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * This is a UNIT TEST for the CandleAggregationService.
 * It tests that the aggregation logic works correctly WITHOUT needing a real database.
 *
 * HOW?
 * ----
 * We use Mockito to create a FAKE (mock) repository. Instead of querying Cassandra,
 * the mock returns whatever test data we tell it to return.
 *
 * @ExtendWith(MockitoExtension.class) = enables Mockito annotations (@Mock, @InjectMocks)
 * @Mock = creates a fake version of the repository
 * @InjectMocks = creates a real CandleAggregationService but injects the fake repository into it
 */
@ExtendWith(MockitoExtension.class)
class CandleAggregationServiceTest {

    @Mock
    private StockCandleRepository stockCandleRepository;  // FAKE repository

    @InjectMocks
    private CandleAggregationService aggregationService;  // REAL service with fake repo injected

    private Instant startRange;
    private Instant endRange;

    @BeforeEach
    void setUp() {
        // Set up the date range we'll use in our tests
        startRange = Instant.parse("2024-01-15T09:15:00Z");
        endRange = Instant.parse("2024-01-15T10:15:00Z");
    }

    /**
     * TEST 1: What happens when the database has NO data?
     * Expected: should return an empty list
     */
    @Test
    void testGetAggregatedCandles_EmptyDb() {
        // Tell the mock: "when someone asks for RELIANCE data, return empty list"
        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(Collections.emptyList());

        // Call the service method
        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "15m", startRange, endRange);

        // Verify: result should be empty
        assertTrue(result.isEmpty());

        // Verify: the repository was called exactly once
        verify(stockCandleRepository, times(1)).findBySymbolAndDateRange("RELIANCE", startRange, endRange);
    }

    /**
     * TEST 2: Aggregate 5 one-minute candles into a single 5-minute candle
     * Input:  5 candles from 09:15 to 09:19 (all in the same 5-min bucket)
     * Expected: 1 aggregated candle for the 09:15 bucket
     */
    @Test
    void testGetAggregatedCandles_5mAggregation() {
        // Create 5 fake 1-minute candles
        List<StockCandle> mockCandles = new ArrayList<>();
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:15:00Z", 10.0, 12.0, 9.0, 11.0, 100L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:16:00Z", 11.0, 13.0, 10.5, 12.5, 150L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:17:00Z", 12.5, 14.0, 11.0, 12.0, 200L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:18:00Z", 12.0, 12.5, 9.5, 10.0, 80L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:19:00Z", 10.0, 11.5, 9.8, 10.5, 120L));

        // Tell mock to return these candles
        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(mockCandles);

        // Call the service
        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "5m", startRange, endRange);

        // Should produce exactly 1 aggregated candle
        assertEquals(1, result.size());
        CandleDto aggregated = result.get(0);

        // Verify the aggregated values:
        assertEquals(Instant.parse("2024-01-15T09:15:00Z"), aggregated.getDatetime());
        assertEquals(10.0, aggregated.getOpen());    // Open of first candle (09:15)
        assertEquals(14.0, aggregated.getHigh());    // Max high across all (09:17 has 14.0)
        assertEquals(9.0, aggregated.getLow());      // Min low across all (09:15 has 9.0)
        assertEquals(10.5, aggregated.getClose());   // Close of last candle (09:19)
        assertEquals(650L, aggregated.getVolume());   // Sum: 100+150+200+80+120 = 650
    }

    /**
     * TEST 3: What happens when some minutes are MISSING? (gaps in data)
     * Input:  only candles at 09:15, 09:17, 09:19 (09:16 and 09:18 are missing)
     * Expected: still aggregates correctly into one 5-min candle
     */
    @Test
    void testGetAggregatedCandles_MissingMinutes() {
        List<StockCandle> mockCandles = new ArrayList<>();
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:15:00Z", 100.0, 105.0, 99.0, 102.0, 1000L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:17:00Z", 102.0, 108.0, 101.0, 106.0, 1500L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:19:00Z", 106.0, 107.0, 103.0, 104.0, 1200L));

        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(mockCandles);

        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "5m", startRange, endRange);

        // All 3 candles fall in the same 5-minute bucket (09:15-09:19)
        assertEquals(1, result.size());
        CandleDto aggregated = result.get(0);

        assertEquals(Instant.parse("2024-01-15T09:15:00Z"), aggregated.getDatetime());
        assertEquals(100.0, aggregated.getOpen());   // Open of 09:15
        assertEquals(108.0, aggregated.getHigh());   // Max high (09:17 = 108.0)
        assertEquals(99.0, aggregated.getLow());     // Min low (09:15 = 99.0)
        assertEquals(104.0, aggregated.getClose());  // Close of 09:19
        assertEquals(3700L, aggregated.getVolume()); // 1000 + 1500 + 1200
    }

    /**
     * TEST 4: Candles that CROSS an hour boundary
     * Input:  candles at 09:58, 09:59, 10:00, 10:01
     * Expected: 2 buckets for 15m timeframe:
     *   - 09:45 bucket (contains 09:58, 09:59)
     *   - 10:00 bucket (contains 10:00, 10:01)
     */
    @Test
    void testGetAggregatedCandles_AcrossHourBoundaries() {
        List<StockCandle> mockCandles = new ArrayList<>();
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:58:00Z", 10.0, 11.0, 9.0, 10.5, 100L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:59:00Z", 10.5, 12.0, 10.0, 11.5, 150L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T10:00:00Z", 11.5, 13.0, 11.0, 12.0, 200L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T10:01:00Z", 12.0, 12.5, 11.8, 12.2, 80L));

        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(mockCandles);

        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "15m", startRange, endRange);

        // Should produce 2 buckets
        assertEquals(2, result.size());

        // First bucket: 09:45 (contains 09:58 and 09:59)
        CandleDto firstBucket = result.get(0);
        assertEquals(Instant.parse("2024-01-15T09:45:00Z"), firstBucket.getDatetime());
        assertEquals(10.0, firstBucket.getOpen());
        assertEquals(12.0, firstBucket.getHigh());
        assertEquals(9.0, firstBucket.getLow());
        assertEquals(11.5, firstBucket.getClose());
        assertEquals(250L, firstBucket.getVolume());

        // Second bucket: 10:00 (contains 10:00 and 10:01)
        CandleDto secondBucket = result.get(1);
        assertEquals(Instant.parse("2024-01-15T10:00:00Z"), secondBucket.getDatetime());
        assertEquals(11.5, secondBucket.getOpen());
        assertEquals(13.0, secondBucket.getHigh());
        assertEquals(11.0, secondBucket.getLow());
        assertEquals(12.2, secondBucket.getClose());
        assertEquals(280L, secondBucket.getVolume());
    }

    // ===== HELPER METHOD: Creates a fake StockCandle for testing =====
    private StockCandle createCandle(String symbol, String datetime, double open, double high, double low, double close, long volume) {
        StockCandleKey key = new StockCandleKey(symbol, Instant.parse(datetime));
        return new StockCandle(key, open, high, low, close, volume);
    }
}
