package com.stock.aggregator.util;

import com.stock.aggregator.model.StockCandle;
import com.stock.aggregator.model.StockCandleKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * This class loads stock data from a CSV file into Cassandra when the app starts.
 * It runs ONCE, automatically, right after Spring Boot finishes starting up.
 *
 * HOW DOES IT WORK?
 * -----------------
 * 1. Spring Boot starts
 * 2. All beans are created (@Controller, @Service, etc.)
 * 3. Spring finds this class because it implements CommandLineRunner
 * 4. Spring calls the run() method
 * 5. run() reads stock_data.csv and saves each row into the Cassandra database
 *
 * CommandLineRunner = an interface with one method: run()
 *   Spring calls run() automatically after the app starts.
 *   Perfect for one-time initialization tasks like loading data!
 *
 * @Component = tells Spring "create an instance of this class and manage it"
 */
@Component
public class DataIngestionRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataIngestionRunner.class);

    // CassandraTemplate = a helper class from Spring that lets us execute Cassandra operations
    @Autowired
    private CassandraTemplate cassandraTemplate;

    // @Value reads values from application.yml
    // If "ingestion.csv-path" is not found in yml, it defaults to "../stock_data.csv"
    @Value("${ingestion.csv-path:../stock_data.csv}")
    private String csvPath;

    // Can be set to false in application.yml to skip loading data
    @Value("${ingestion.enabled:true}")
    private boolean ingestionEnabled;

    // Formatter to parse dates from the CSV file (format: "2024-01-15 09:15:00")
    private static final DateTimeFormatter CSV_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    // How many rows to save at once (batch = faster than one-by-one)
    private static final int BATCH_SIZE = 500;

    /**
     * This method runs automatically when the Spring Boot app starts.
     * It reads stock_data.csv and saves each row into Cassandra.
     */
    @Override
    public void run(String... args) throws Exception {
        // If ingestion is disabled in config, skip everything
        if (!ingestionEnabled) {
            log.info("Stock data ingestion is disabled via configuration.");
            return;
        }

        log.info("Starting stock market data ingestion pipeline...");

        // ===== STEP 1: Find the CSV file =====
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

        // ===== STEP 2: Read the CSV file line by line =====
        int successCount = 0;
        int lineCount = 0;
        List<StockCandle> batch = new ArrayList<>();  // temporary list to hold rows before saving

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            // Read and skip the header line (e.g., "symbol,datetime,open,high,low,close,volume")
            String header = br.readLine();
            if (header == null) {
                log.error("CSV file is empty!");
                return;
            }

            // Read each data line
            String line;
            while ((line = br.readLine()) != null) {
                lineCount++;

                // Skip empty lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // ===== STEP 3: Parse each CSV line =====
                    // CSV format: SYMBOL,datetime,open,high,low,close,volume
                    // Example:    RELIANCE,2024-01-15 09:15:00,2450.5,2455.0,2448.0,2453.2,50000
                    String[] parts = line.split(",");

                    // Each row must have at least 7 columns
                    if (parts.length < 7) {
                        log.warn("Skipping malformed row at line {}: {}", lineCount, line);
                        continue;
                    }

                    // Extract each column
                    String symbol = parts[0].trim().toUpperCase();          // "RELIANCE"
                    String datetimeStr = parts[1].trim();                    // "2024-01-15 09:15:00"
                    Double open = Double.parseDouble(parts[2].trim());       // 2450.5
                    Double high = Double.parseDouble(parts[3].trim());       // 2455.0
                    Double low = Double.parseDouble(parts[4].trim());        // 2448.0
                    Double close = Double.parseDouble(parts[5].trim());      // 2453.2
                    Long volume = Long.parseLong(parts[6].trim());           // 50000

                    // Parse the date string into an Instant
                    Instant datetime = Instant.from(CSV_DATE_FORMATTER.parse(datetimeStr));

                    // Create the model objects
                    StockCandleKey key = new StockCandleKey(symbol, datetime);
                    StockCandle candle = new StockCandle(key, open, high, low, close, volume);

                    // Add to the batch
                    batch.add(candle);

                    // ===== STEP 4: Save in batches for better performance =====
                    // Instead of saving 1 row at a time, we save 500 at once
                    if (batch.size() >= BATCH_SIZE) {
                        saveBatch(batch);
                        successCount += batch.size();
                        batch.clear();  // empty the batch for the next set
                    }

                } catch (Exception e) {
                    log.error("Failed to parse row at line {}: '{}'. Error: {}", lineCount, line, e.getMessage());
                }
            }

            // Save any remaining rows that didn't fill a complete batch
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

    /**
     * Saves a batch of candles to Cassandra in one go.
     * This is much faster than saving one row at a time!
     */
    private void saveBatch(List<StockCandle> batch) {
        log.debug("Batch inserting {} stock candles...", batch.size());
        cassandraTemplate.batchOps().insert(batch).execute();
    }
}
