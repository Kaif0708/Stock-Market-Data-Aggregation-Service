package com.stock.aggregator.controller;

import com.stock.aggregator.dto.CandleDto;
import com.stock.aggregator.dto.CandleResponse;
import com.stock.aggregator.exception.InvalidRequestException;
import com.stock.aggregator.exception.ResourceNotFoundException;
import com.stock.aggregator.service.CandleAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * WHAT IS A CONTROLLER?
 * ---------------------
 * A Controller handles incoming HTTP requests from users/clients.
 * When someone calls "GET http://localhost:8080/api/v1/candles?symbol=RELIANCE&...",
 * THIS class receives that request.
 *
 * THE FLOW:
 * ---------
 * User/Browser -> HTTP Request -> Controller -> Service -> Repository -> Cassandra DB
 *                                                                          |
 * User/Browser <- HTTP Response <- Controller <- Service <- Repository <----
 *
 * @RestController = @Controller + @ResponseBody
 *   - @Controller = marks this as a web controller
 *   - @ResponseBody = return values are automatically converted to JSON
 *
 * @RequestMapping("/api/v1") = ALL endpoints in this controller start with /api/v1
 */
@RestController
@RequestMapping("/api/v1")
public class StockCandleController {

    // Logger for printing debug/info messages to console
    private static final Logger log = LoggerFactory.getLogger(StockCandleController.class);

    // Spring automatically creates and injects the service here
    @Autowired
    private CandleAggregationService candleAggregationService;

    // List of valid timeframes the user can request
    private static final List<String> SUPPORTED_TIMEFRAMES = Arrays.asList("1m", "5m", "15m", "30m", "1h", "1d");

    // Date formatters to parse user-provided date strings
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    /**
     * THE MAIN API ENDPOINT
     * ---------------------
     * URL: GET /api/v1/candles
     *
     * Required query parameters:
     *   - symbol     : stock ticker (e.g., "RELIANCE")
     *   - timeframe  : aggregation period (e.g., "15m")
     *   - start_date : start of date range (e.g., "2024-01-15 09:15:00")
     *   - end_date   : end of date range
     *
     * Optional query parameters:
     *   - page : page number for pagination (starts from 0)
     *   - size : how many candles per page
     *
     * @RequestParam = extracts values from the URL query string
     *   e.g., ?symbol=RELIANCE -> symbol variable gets value "RELIANCE"
     */
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

        // ===== STEP 1: VALIDATE THE INPUT =====

        // Check: is "symbol" provided?
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidRequestException("Missing required parameter: symbol");
        }

        // Check: is "timeframe" provided?
        if (timeframe == null || timeframe.trim().isEmpty()) {
            throw new InvalidRequestException("Missing required parameter: timeframe");
        }

        // Check: is the timeframe one of the supported values?
        String tfLower = timeframe.trim().toLowerCase();
        if (!SUPPORTED_TIMEFRAMES.contains(tfLower)) {
            throw new InvalidRequestException(
                    "Unsupported timeframe '" + timeframe + "'. Supported: " + SUPPORTED_TIMEFRAMES);
        }

        // Parse the date strings into Instant objects
        Instant startDate = parseDateTime(startDateStr, "start_date");
        Instant endDate = parseDateTime(endDateStr, "end_date");

        // Check: start date should be before or equal to end date
        if (startDate.isAfter(endDate)) {
            throw new InvalidRequestException("start_date cannot be after end_date");
        }

        // ===== STEP 2: GET THE DATA FROM THE SERVICE =====
        List<CandleDto> allCandles = candleAggregationService.getAggregatedCandles(symbol, tfLower, startDate, endDate);

        // Check: did we find any data?
        if (allCandles.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No candlestick data found for symbol: '" + symbol + "' in range [" + startDateStr + " to " + endDateStr + "]");
        }

        // ===== STEP 3: OPTIONAL PAGINATION =====
        // If user provides page & size, return only a subset of results
        List<CandleDto> pagedCandles = allCandles;

        if (page != null && size != null) {
            // Validate pagination parameters
            if (page < 0 || size <= 0) {
                throw new InvalidRequestException("Pagination parameters 'page' must be >= 0 and 'size' must be > 0");
            }

            // Calculate which portion of the list to return
            int startIdx = page * size;  // e.g., page=1, size=10 -> start at index 10

            if (startIdx >= allCandles.size()) {
                // Page number is too high, return empty list
                pagedCandles = Collections.emptyList();
            } else {
                // Get items from startIdx to endIdx
                int endIdx = Math.min(startIdx + size, allCandles.size());
                pagedCandles = allCandles.subList(startIdx, endIdx);
            }
        }

        // ===== STEP 4: BUILD AND RETURN THE RESPONSE =====
        CandleResponse response = new CandleResponse(
                symbol.toUpperCase(),   // always uppercase the symbol
                tfLower,                // the normalized timeframe
                pagedCandles,           // the candle data
                pagedCandles.size()     // count of candles
        );

        long controllerEnd = System.currentTimeMillis();
        log.info("Request completed in {}ms. Returning {} candles.", (controllerEnd - controllerStart), pagedCandles.size());

        // ResponseEntity.ok() wraps the response with HTTP status 200 (OK)
        return ResponseEntity.ok(response);
    }

    /**
     * HELPER METHOD: Parse a date string into an Instant
     * --------------------------------------------------
     * The user might send dates in different formats. This method tries them all:
     *   1. ISO 8601: "2024-01-15T09:15:00Z"
     *   2. Simple:   "2024-01-15 09:15:00"
     *   3. Date only: "2024-01-15" (start -> 00:00:00, end -> 23:59:59)
     */
    private Instant parseDateTime(String dateStr, String paramName) {
        // Check if the date string is provided
        if (dateStr == null || dateStr.trim().isEmpty()) {
            throw new InvalidRequestException("Parameter " + paramName + " is required.");
        }

        String cleanStr = dateStr.trim();

        // Try Format 1: ISO 8601 (e.g., "2024-01-15T09:15:00Z")
        try {
            return Instant.parse(cleanStr);
        } catch (Exception e1) {
            // Format 1 didn't work, try the next one
        }

        // Try Format 2: "yyyy-MM-dd HH:mm:ss" (e.g., "2024-01-15 09:15:00")
        try {
            return Instant.from(DATETIME_FORMATTER.parse(cleanStr));
        } catch (Exception e2) {
            // Format 2 didn't work, try the next one
        }

        // Try Format 3: Date only "yyyy-MM-dd" (e.g., "2024-01-15")
        try {
            LocalDate localDate = LocalDate.parse(cleanStr, DATE_ONLY_FORMATTER);

            // For start_date: use the beginning of the day (00:00:00)
            if (paramName.contains("start")) {
                return localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            }
            // For end_date: use the end of the day (23:59:59)
            else {
                return localDate.atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
            }
        } catch (Exception e3) {
            // None of the formats worked
        }

        // If we reach here, none of the formats worked -> throw error
        throw new InvalidRequestException(
                "Invalid value for " + paramName + ": '" + dateStr
                        + "'. Expected format: yyyy-MM-dd HH:mm:ss, ISO-8601 (yyyy-MM-ddTHH:mm:ssZ), or yyyy-MM-dd");
    }
}
