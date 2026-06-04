package com.stock.aggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class StockAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(StockAggregatorApplication.class, args);
    }
}
