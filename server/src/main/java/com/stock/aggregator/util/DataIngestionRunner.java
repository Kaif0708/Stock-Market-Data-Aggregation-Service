package com.stock.aggregator.util;

import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.model.StockCandleKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataIngestionRunner implements CommandLineRunner {

    private final CassandraTemplate cassandraTemplate;

    @Value("${ingestion.csv-path:../stock_data.csv}")
    private String csvPath;

    @Value("${ingestion.enabled:true}")
    private boolean ingestionEnabled;

    private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    private static final int BATCH_SIZE = 500;

    @Override
    public void run(String... args) throws Exception {
        if (!ingestionEnabled) {
            log.info("Stock data ingestion is disabled via configuration.");
            return;
        }

        log.info("Starting stock market data ingestion pipeline...");

        // Find the CSV file (checking configured path, then current working directory)
        File file = new File(csvPath);
        if (!file.exists()) {
            log.warn("CSV file not found at: {}. Trying local fallback 'stock_data.csv'...", file.getAbsolutePath());
            file = new File("stock_data.csv");
        }

        if (!file.exists()) {
            log.error("Could not find CSV file for ingestion at '{}' or './stock_data.csv'. Skipping ingestion.", csvPath);
            return;
        }

        log.info("Found stock data CSV file at: {}", file.getAbsolutePath());
        long start = System.currentTimeMillis();

        int successCount = 0;
        int lineCount = 0;
        List<StockCandle> batch = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            // Read header
            String header = br.readLine();
            if (header == null) {
                log.error("CSV file is empty!");
                return;
            }

            while ((line = br.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    String[] parts = line.split(",");
                    if (parts.length < 7) {
                        log.warn("Skipping malformed row at line {}: {}", lineCount, line);
                        continue;
                    }

                    String symbol = parts[0].trim().toUpperCase();
                    String datetimeStr = parts[1].trim();
                    Double open = Double.parseDouble(parts[2].trim());
                    Double high = Double.parseDouble(parts[3].trim());
                    Double low = Double.parseDouble(parts[4].trim());
                    Double close = Double.parseDouble(parts[5].trim());
                    Long volume = Long.parseLong(parts[6].trim());

                    Instant datetime = Instant.from(CSV_DATE_FORMATTER.parse(datetimeStr));

                    StockCandleKey key = new StockCandleKey(symbol, datetime);
                    StockCandle candle = new StockCandle(key, open, high, low, close, volume);
                    batch.add(candle);

                    if (batch.size() >= BATCH_SIZE) {
                        saveBatch(batch);
                        successCount += batch.size();
                        batch.clear();
                    }

                } catch (Exception e) {
                    log.error("Failed to parse row at line {}: '{}'. Error: {}", lineCount, line, e.getMessage());
                }
            }

            // Save remaining
            if (!batch.isEmpty()) {
                saveBatch(batch);
                successCount += batch.size();
            }

        } catch (IOException e) {
            log.error("IOException occurred reading CSV file: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - start;
        log.info("Ingestion pipeline finished in {}ms. Successfully ingested {} candles into Apache Cassandra.",
                duration, successCount);
    }

    private void saveBatch(List<StockCandle> batch) {
        log.debug("Batch inserting {} stock candles...", batch.size());
        cassandraTemplate.batchOps().insert(batch).execute();
    }
}
