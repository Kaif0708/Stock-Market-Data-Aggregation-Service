package com.stock.aggregator.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WHAT IS THIS CLASS?
 * -------------------
 * This is a Configuration class. It tells Spring Boot HOW to set up caching.
 *
 * WHAT IS CACHING?
 * ----------------
 * Caching = storing results of expensive operations in memory so we don't
 * have to recalculate them every time.
 *
 * Example: If someone asks for "RELIANCE 15m candles for Jan 15",
 * the first time we calculate it from the database. The second time the
 * same request comes, we just return the stored (cached) result — much faster!
 *
 * @Configuration = tells Spring "this class contains setup/configuration code"
 * @Bean = tells Spring "create this object and manage it for me"
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // ConcurrentMapCacheManager = a simple in-memory cache (like a HashMap)
        // "aggregatedCandles" = the name of our cache (we reference this name in the service)
        return new ConcurrentMapCacheManager("aggregatedCandles");
    }
}
