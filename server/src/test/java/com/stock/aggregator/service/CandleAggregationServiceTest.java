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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandleAggregationServiceTest {

    @Mock
    private StockCandleRepository stockCandleRepository;

    @InjectMocks
    private CandleAggregationService aggregationService;

    private Instant startRange;
    private Instant endRange;

    @BeforeEach
    void setUp() {
        startRange = parseInstant("2024-01-15T09:15:00Z");
        endRange = parseInstant("2024-01-15T10:15:00Z");
    }

    @Test
    void testGetAggregatedCandles_EmptyDb() {
        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(Collections.emptyList());

        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "15m", startRange, endRange);

        assertTrue(result.isEmpty());
        verify(stockCandleRepository, times(1)).findBySymbolAndDateRange("RELIANCE", startRange, endRange);
    }

    @Test
    void testGetAggregatedCandles_5mAggregation() {
        // Mock 1-minute candles from 09:15 to 09:19 (5 candles)
        List<StockCandle> mockCandles = new ArrayList<>();
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:15:00Z", 10.0, 12.0, 9.0, 11.0, 100L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:16:00Z", 11.0, 13.0, 10.5, 12.5, 150L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:17:00Z", 12.5, 14.0, 11.0, 12.0, 200L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:18:00Z", 12.0, 12.5, 9.5, 10.0, 80L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:19:00Z", 10.0, 11.5, 9.8, 10.5, 120L));

        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(mockCandles);

        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "5m", startRange, endRange);

        assertEquals(1, result.size());
        CandleDto aggregated = result.get(0);

        assertEquals(parseInstant("2024-01-15T09:15:00Z"), aggregated.getDatetime());
        assertEquals(10.0, aggregated.getOpen()); // Open of 09:15:00
        assertEquals(14.0, aggregated.getHigh()); // Max High (09:17:00 is 14.0)
        assertEquals(9.0, aggregated.getLow());   // Min Low (09:15:00 is 9.0)
        assertEquals(10.5, aggregated.getClose()); // Close of 09:19:00
        assertEquals(650L, aggregated.getVolume()); // Sum volume (100+150+200+80+120)
    }

    @Test
    void testGetAggregatedCandles_MissingMinutes() {
        // Mock 1-minute candles but with missing intervals: 09:15, 09:17, 09:19 (gaps)
        List<StockCandle> mockCandles = new ArrayList<>();
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:15:00Z", 100.0, 105.0, 99.0, 102.0, 1000L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:17:00Z", 102.0, 108.0, 101.0, 106.0, 1500L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:19:00Z", 106.0, 107.0, 103.0, 104.0, 1200L));

        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(mockCandles);

        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "5m", startRange, endRange);

        // This covers one 5-minute bucket: 09:15-09:19
        assertEquals(1, result.size());
        CandleDto aggregated = result.get(0);

        assertEquals(parseInstant("2024-01-15T09:15:00Z"), aggregated.getDatetime());
        assertEquals(100.0, aggregated.getOpen()); // Open of 09:15
        assertEquals(108.0, aggregated.getHigh()); // Max High of 09:17
        assertEquals(99.0, aggregated.getLow());   // Min Low of 09:15
        assertEquals(104.0, aggregated.getClose()); // Close of 09:19
        assertEquals(3700L, aggregated.getVolume()); // 1000 + 1500 + 1200
    }

    @Test
    void testGetAggregatedCandles_AcrossHourBoundaries() {
        // Mock candles crossing the hour mark: 09:58, 09:59 (bucket 09:45) and 10:00, 10:01 (bucket 10:00)
        List<StockCandle> mockCandles = new ArrayList<>();
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:58:00Z", 10.0, 11.0, 9.0, 10.5, 100L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T09:59:00Z", 10.5, 12.0, 10.0, 11.5, 150L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T10:00:00Z", 11.5, 13.0, 11.0, 12.0, 200L));
        mockCandles.add(createCandle("RELIANCE", "2024-01-15T10:01:00Z", 12.0, 12.5, 11.8, 12.2, 80L));

        when(stockCandleRepository.findBySymbolAndDateRange("RELIANCE", startRange, endRange))
                .thenReturn(mockCandles);

        List<CandleDto> result = aggregationService.getAggregatedCandles("RELIANCE", "15m", startRange, endRange);

        // We expect two 15-minute buckets: 09:45 and 10:00
        assertEquals(2, result.size());

        CandleDto firstBucket = result.get(0);
        assertEquals(parseInstant("2024-01-15T09:45:00Z"), firstBucket.getDatetime());
        assertEquals(10.0, firstBucket.getOpen());
        assertEquals(12.0, firstBucket.getHigh());
        assertEquals(9.0, firstBucket.getLow());
        assertEquals(11.5, firstBucket.getClose());
        assertEquals(250L, firstBucket.getVolume());

        CandleDto secondBucket = result.get(1);
        assertEquals(parseInstant("2024-01-15T10:00:00Z"), secondBucket.getDatetime());
        assertEquals(11.5, secondBucket.getOpen());
        assertEquals(13.0, secondBucket.getHigh());
        assertEquals(11.0, secondBucket.getLow());
        assertEquals(12.2, secondBucket.getClose());
        assertEquals(280L, secondBucket.getVolume());
    }

    private StockCandle createCandle(String symbol, String datetime, double open, double high, double low, double close, long volume) {
        StockCandleKey key = new StockCandleKey(symbol, parseInstant(datetime));
        return new StockCandle(key, open, high, low, close, volume);
    }

    private Instant parseInstant(String datetimeStr) {
        return Instant.parse(datetimeStr);
    }
}
