package com.example.investmentdatascannerservice.config;

import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import com.example.investmentdatascannerservice.service.CircuitBreakerMonitoringService;
import lombok.RequiredArgsConstructor;

/**
 * Health Indicator для Circuit Breaker
 * 
 * Предоставляет информацию о состоянии Circuit Breaker для Spring Boot Actuator.
 */
@Component
@RequiredArgsConstructor
public class CircuitBreakerHealthIndicator implements HealthIndicator {

    private final CircuitBreakerMonitoringService monitoringService;

    @Override
    public Health health() {
        try {
            Map<String, Object> stats = monitoringService.getCircuitBreakerStats();
            String state = (String) stats.get("state");
            boolean isHealthy = monitoringService.isHealthy();

            if (isHealthy) {
                return Health.up().withDetail("circuitBreaker", "T-Invest API")
                        .withDetail("state", state).withDetail("status", "Available")
                        .withDetail("failureRate", stats.get("failureRate"))
                        .withDetail("successRate", stats.get("successRate"))
                        .withDetail("numberOfCalls", stats.get("numberOfCalls"))
                        .withDetail("timestamp", System.currentTimeMillis()).build();
            } else {
                return Health.down().withDetail("circuitBreaker", "T-Invest API")
                        .withDetail("state", state).withDetail("status", "Unavailable")
                        .withDetail("failureRate", stats.get("failureRate"))
                        .withDetail("successRate", stats.get("successRate"))
                        .withDetail("numberOfCalls", stats.get("numberOfCalls"))
                        .withDetail("timestamp", System.currentTimeMillis()).build();
            }
        } catch (Exception e) {
            return Health.down().withDetail("circuitBreaker", "T-Invest API")
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis()).build();
        }
    }
}
