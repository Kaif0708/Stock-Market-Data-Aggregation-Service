package com.stock.aggregator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WHAT IS CORS?
 * -------------
 * CORS = Cross-Origin Resource Sharing.
 *
 * By default, a web browser BLOCKS requests from one website to another.
 * For example: if your frontend runs on http://localhost:3000 and your
 * backend runs on http://localhost:8080, the browser will block the request.
 *
 * This config tells Spring: "Allow requests from ANY origin (any website/port)"
 * so our Vue.js web dashboard can talk to this Spring Boot API.
 *
 * WebMvcConfigurer = an interface from Spring that lets us customize web settings.
 * We override the addCorsMappings() method to configure CORS rules.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                    // Apply to ALL endpoints (/**)
                .allowedOriginPatterns("*")            // Allow requests from ANY origin
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // Allow these HTTP methods
                .allowedHeaders("*")                   // Allow ANY headers
                .allowCredentials(true);               // Allow cookies/auth headers
    }
}
