package com.stock.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class StockCliClient {

    private static final String BASE_URL = "http://localhost:8080/api/v1/candles";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Error: Missing parameters.");
            System.err.println("Usage: java -jar client.jar <symbol> <timeframe> <start_date> <end_date>");
            System.err.println("Example: java -jar client.jar RELIANCE 15m \"2024-01-15 09:15:00\" \"2024-01-15 15:30:00\"");
            System.exit(1);
        }

        String symbol = args[0];
        String timeframe = args[1];
        String startDate = args[2];
        String endDate = args[3];

        try {
            // Encode parameters for the URL
            String queryParams = String.format("symbol=%s&timeframe=%s&start_date=%s&end_date=%s",
                    URLEncoder.encode(symbol, StandardCharsets.UTF_8),
                    URLEncoder.encode(timeframe, StandardCharsets.UTF_8),
                    URLEncoder.encode(startDate, StandardCharsets.UTF_8),
                    URLEncoder.encode(endDate, StandardCharsets.UTF_8)
            );

            URI targetUri = URI.create(BASE_URL + "?" + queryParams);
            System.out.println("Connecting to endpoint: " + BASE_URL);
            System.out.println("Executing GET request...");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            long startTime = System.currentTimeMillis();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long duration = System.currentTimeMillis() - startTime;

            if (response.statusCode() == 200) {
                // Parse and map response DTOs
                CandleResponse candleResponse = OBJECT_MAPPER.readValue(response.body(), CandleResponse.class);
                
                System.out.println("Response received in " + duration + "ms.");
                System.out.println("=== Fetched Candle Data ===");
                System.out.printf("Symbol: %s | Timeframe: %s | Total Candles: %d\n",
                        candleResponse.getSymbol(),
                        candleResponse.getTimeframe(),
                        candleResponse.getCount()
                );

                List<CandleDto> candles = candleResponse.getCandles();
                for (int i = 0; i < candles.size(); i++) {
                    CandleDto candle = candles.get(i);
                    // Match the assignment's exact template format: [index][datetime] | O: [open] ...
                    // E.g., 12024-01-15T09:15:00Z | O: 2450.50 | H: ...
                    System.out.printf("%d%s | O: %.2f | H: %.2f | L: %.2f | C: %.2f | V: %d\n",
                            (i + 1),
                            candle.getDatetime(),
                            candle.getOpen(),
                            candle.getHigh(),
                            candle.getLow(),
                            candle.getClose(),
                            candle.getVolume()
                    );
                }
                System.out.println("===========================");

            } else {
                System.err.println("HTTP Error: Status Code " + response.statusCode());
                try {
                    // Try parsing structured error response from Server's Global Exception Handler
                    ErrorResponse err = OBJECT_MAPPER.readValue(response.body(), ErrorResponse.class);
                    System.err.println("Error Message: " + err.getMessage());
                } catch (Exception e) {
                    // Fail back to printing raw body
                    System.err.println("Response Body: " + response.body());
                }
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("An unexpected error occurred during client execution: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // --- Response mappings mirroring Server DTOs ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandleResponse {
        private String symbol;
        private String timeframe;
        private List<CandleDto> candles;
        private int count;

        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public String getTimeframe() { return timeframe; }
        public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

        public List<CandleDto> getCandles() { return candles; }
        public void setCandles(List<CandleDto> candles) { this.candles = candles; }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CandleDto {
        private String datetime;
        private double open;
        private double high;
        private double low;
        private double close;
        private long volume;

        public String getDatetime() { return datetime; }
        public void setDatetime(String datetime) { this.datetime = datetime; }

        public double getOpen() { return open; }
        public void setOpen(double open) { this.open = open; }

        public double getHigh() { return high; }
        public void setHigh(double high) { this.high = high; }

        public double getLow() { return low; }
        public void setLow(double low) { this.low = low; }

        public double getClose() { return close; }
        public void setClose(double close) { this.close = close; }

        public long getVolume() { return volume; }
        public void setVolume(long volume) { this.volume = volume; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorResponse {
        private String error;
        private String message;

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
