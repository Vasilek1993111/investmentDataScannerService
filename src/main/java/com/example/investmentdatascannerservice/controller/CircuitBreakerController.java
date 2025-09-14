package com.example.investmentdatascannerservice.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.service.CircuitBreakerMonitoringService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для мониторинга Circuit Breaker
 * 
 * Предоставляет endpoints для получения статистики и управления Circuit Breaker.
 */
@Slf4j
@RestController
@RequestMapping("/api/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerController {

    private final CircuitBreakerMonitoringService monitoringService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * GET /api/circuit-breaker/stats Получение статистики Circuit Breaker
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = monitoringService.getCircuitBreakerStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting circuit breaker stats", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get circuit breaker stats");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/circuit-breaker/health Проверка здоровья Circuit Breaker
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("isHealthy", monitoringService.isHealthy());
            health.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error checking circuit breaker health", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to check circuit breaker health");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/circuit-breaker/detailed Получение детальной информации о Circuit Breaker
     */
    @GetMapping("/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedStats() {
        try {
            Map<String, Object> stats = monitoringService.getDetailedStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting detailed circuit breaker stats", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get detailed circuit breaker stats");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * POST /api/circuit-breaker/reset Сброс состояния Circuit Breaker
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest-api");
            circuitBreaker.reset();

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Circuit breaker reset successfully");
            response.put("state", circuitBreaker.getState().name());
            response.put("timestamp", System.currentTimeMillis());

            log.info("Circuit breaker reset requested and completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resetting circuit breaker", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to reset circuit breaker");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * POST /api/circuit-breaker/transition/{state} Принудительный переход в определенное состояние
     */
    @PostMapping("/transition/{state}")
    public ResponseEntity<Map<String, Object>> transitionToState(@PathVariable String state) {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest-api");

            Map<String, Object> response = new HashMap<>();

            switch (state.toUpperCase()) {
                case "OPEN" -> {
                    circuitBreaker.transitionToOpenState();
                    response.put("message", "Circuit breaker transitioned to OPEN state");
                }
                case "CLOSED" -> {
                    circuitBreaker.transitionToClosedState();
                    response.put("message", "Circuit breaker transitioned to CLOSED state");
                }
                case "HALF_OPEN" -> {
                    circuitBreaker.transitionToHalfOpenState();
                    response.put("message", "Circuit breaker transitioned to HALF_OPEN state");
                }
                default -> {
                    response.put("error", "Invalid state. Use OPEN, CLOSED, or HALF_OPEN");
                    return ResponseEntity.badRequest().body(response);
                }
            }

            response.put("state", circuitBreaker.getState().name());
            response.put("timestamp", System.currentTimeMillis());

            log.info("Circuit breaker transitioned to {} state", state);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error transitioning circuit breaker to state: {}", state, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to transition circuit breaker");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * GET /api/circuit-breaker/config Получение конфигурации Circuit Breaker
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        try {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest-api");
            var config = circuitBreaker.getCircuitBreakerConfig();

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("failureRateThreshold", config.getFailureRateThreshold());
            configMap.put("waitIntervalFunctionInOpenState",
                    String.valueOf(config.getWaitIntervalFunctionInOpenState()));
            configMap.put("slidingWindowSize", config.getSlidingWindowSize());
            configMap.put("minimumNumberOfCalls", config.getMinimumNumberOfCalls());
            configMap.put("permittedNumberOfCallsInHalfOpenState",
                    config.getPermittedNumberOfCallsInHalfOpenState());
            configMap.put("automaticTransitionFromOpenToHalfOpenEnabled",
                    config.isAutomaticTransitionFromOpenToHalfOpenEnabled());
            configMap.put("slowCallRateThreshold", config.getSlowCallRateThreshold());
            configMap.put("slowCallDurationThreshold",
                    config.getSlowCallDurationThreshold().toString());

            return ResponseEntity.ok(configMap);
        } catch (Exception e) {
            log.error("Error getting circuit breaker config", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to get circuit breaker config");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}
