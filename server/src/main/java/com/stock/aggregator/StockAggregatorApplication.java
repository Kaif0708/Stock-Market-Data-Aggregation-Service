package com.stock.aggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * This is the MAIN class that starts the entire Spring Boot application.
 *
 * @SpringBootApplication = combines 3 annotations:
 *   1. @Configuration   -> this class can define beans (objects managed by Spring)
 *   2. @ComponentScan   -> Spring will scan this package & sub-packages for components
 *   3. @EnableAutoConfiguration -> Spring auto-configures things like database, web, etc.
 *
 * @EnableCaching = turns on caching support so we can use @Cacheable in our service
 *                  (this avoids hitting the database for the same query again and again)
 */
@SpringBootApplication
@EnableCaching
public class StockAggregatorApplication {

    public static void main(String[] args) {
        // This single line starts the Spring Boot app:
        // - starts embedded Tomcat server (port 8080)
        // - connects to Cassandra database
        // - scans for all @Controller, @Service, @Repository, @Component classes
        SpringApplication.run(StockAggregatorApplication.class, args);
    }
}
