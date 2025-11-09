package com.example.investmentdatascannerservice.config;

import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import com.example.investmentdatascannerservice.service.CircuitBreakerMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Health Indicator для Circuit Breaker
 * 
 * Предоставляет информацию о состоянии Circuit Breaker для Spring Boot Actuator.
 */
@Slf4j
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
                log.debug("Circuit Breaker health check: state={}, status=Available, failureRate={}, successRate={}, numberOfCalls={}",
                        state, stats.get("failureRate"), stats.get("successRate"), stats.get("numberOfCalls"));
                return Health.up().withDetail("circuitBreaker", "T-Invest API")
                        .withDetail("state", state).withDetail("status", "Available")
                        .withDetail("failureRate", stats.get("failureRate"))
                        .withDetail("successRate", stats.get("successRate"))
                        .withDetail("numberOfCalls", stats.get("numberOfCalls"))
                        .withDetail("timestamp", System.currentTimeMillis()).build();
            } else {
                log.warn("Circuit Breaker health check: state={}, status=Unavailable, failureRate={}, successRate={}, numberOfCalls={}",
                        state, stats.get("failureRate"), stats.get("successRate"), stats.get("numberOfCalls"));
                return Health.down().withDetail("circuitBreaker", "T-Invest API")
                        .withDetail("state", state).withDetail("status", "Unavailable")
                        .withDetail("failureRate", stats.get("failureRate"))
                        .withDetail("successRate", stats.get("successRate"))
                        .withDetail("numberOfCalls", stats.get("numberOfCalls"))
                        .withDetail("timestamp", System.currentTimeMillis()).build();
            }
        } catch (Exception e) {
            log.error("Error checking Circuit Breaker health: {}", e.getMessage(), e);
            return Health.down().withDetail("circuitBreaker", "T-Invest API")
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis()).build();
        }
    }
}
