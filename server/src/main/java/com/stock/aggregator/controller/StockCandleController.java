package com.stock.aggregator.controller;

import com.stock.aggregator.dto.CandleDto;
import com.stock.aggregator.dto.CandleResponse;
import com.stock.aggregator.exception.InvalidRequestException;
import com.stock.aggregator.exception.ResourceNotFoundException;
import com.stock.aggregator.service.CandleAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockCandleController {

    private final CandleAggregationService candleAggregationService;

    private static final List<String> SUPPORTED_TIMEFRAMES = Arrays.asList("1m", "5m", "15m", "30m", "1h", "1d");

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    @GetMapping("/candles")
    public ResponseEntity<CandleResponse> getCandles(
            @RequestParam(value = "symbol") String symbol,
            @RequestParam(value = "timeframe") String timeframe,
            @RequestParam(value = "start_date") String startDateStr,
            @RequestParam(value = "end_date") String endDateStr,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {

        long controllerStart = System.currentTimeMillis();
        log.info("Incoming GET /api/v1/candles | symbol: {}, timeframe: {}, start_date: {}, end_date: {}, page: {}, size: {}",
                symbol, timeframe, startDateStr, endDateStr, page, size);

        // 1. Parameter Validation
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidRequestException("Missing required parameter: symbol");
        }

        if (timeframe == null || timeframe.trim().isEmpty()) {
            throw new InvalidRequestException("Missing required parameter: timeframe");
        }

        String tfLower = timeframe.trim().toLowerCase();
        if (!SUPPORTED_TIMEFRAMES.contains(tfLower)) {
            throw new InvalidRequestException("Unsupported timeframe '" + timeframe + "'. Supported: " + SUPPORTED_TIMEFRAMES);
        }

        Instant startDate = parseDateTime(startDateStr, "start_date");
        Instant endDate = parseDateTime(endDateStr, "end_date");

        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("start_date cannot be after end_date");
        }

        // 2. Data Retrieval
        List<CandleDto> allCandles = candleAggregationService.getAggregatedCandles(symbol, tfLower, startDate, endDate);

        if (allCandles.isEmpty()) {
            throw new ResourceNotFoundException("No candlestick data found for symbol: '" + symbol + "' in range [" + startDateStr + " to " + endDateStr + "]");
        }

        // 3. Optional Pagination
        List<CandleDto> pagedCandles = allCandles;
        if (page != null && size != null) {
            if (page < 0 || size <= 0) {
                throw new InvalidRequestException("Pagination parameters 'page' must be >= 0 and 'size' must be > 0");
            }
            int startIdx = page * size;
            if (startIdx >= allCandles.size()) {
                pagedCandles = Collections.emptyList();
            } else {
                int endIdx = Math.min(startIdx + size, allCandles.size());
                pagedCandles = allCandles.subList(startIdx, endIdx);
            }
        }

        CandleResponse response = CandleResponse.builder()
                .symbol(symbol.toUpperCase())
                .timeframe(tfLower)
                .candles(pagedCandles)
                .count(pagedCandles.size())
                .build();

        long controllerEnd = System.currentTimeMillis();
        log.info("Request completed in {}ms. Returning {} candles.", (controllerEnd - controllerStart), pagedCandles.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Tries parsing datetime using multiple patterns.
     */
    private Instant parseDateTime(String dateStr, String paramName) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new InvalidRequestException("Parameter " + paramName + " is required.");
        }
        String cleanStr = dateStr.trim();
        // Option 1: Try ISO 8601 (e.g. 2024-01-15T09:15:00Z)
        try {
            return Instant.parse(cleanStr);
        } catch (Exception e1) {
            // Option 2: Try yyyy-MM-dd HH:mm:ss in UTC
            try {
                return Instant.from(DATETIME_FORMATTER.parse(cleanStr));
            } catch (Exception e2) {
                // Option 3: Try yyyy-MM-dd in UTC
                try {
                    java.time.LocalDate localDate = java.time.LocalDate.parse(cleanStr, DATE_ONLY_FORMATTER);
                    if (paramName.contains("start")) {
                        return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
                    } else {
                        return localDate.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
                    }
                } catch (Exception e3) {
                    throw new InvalidRequestException("Invalid value for " + paramName + ": '" + dateStr + 
                            "'. Expected format: yyyy-MM-dd HH:mm:ss, ISO-8601 (yyyy-MM-ddTHH:mm:ssZ), or yyyy-MM-dd");
                }
            }
        }
    }
}
