package com.example.investmentdatascannerservice.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для мониторинга Circuit Breaker
 * 
 * Предоставляет метрики и статистику работы Circuit Breaker, интегрируется с Micrometer для
 * мониторинга.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerMonitoringService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final TInvestApiClient tinvestApiClient;

    private Counter circuitBreakerOpenCounter;
    private Counter circuitBreakerClosedCounter;
    private Counter circuitBreakerHalfOpenCounter;
    private Counter circuitBreakerCallNotPermittedCounter;
    private Counter circuitBreakerSuccessCounter;
    private Counter circuitBreakerFailureCounter;

    @PostConstruct
    public void initMetrics() {
        // Счетчики для состояний Circuit Breaker
        circuitBreakerOpenCounter = Counter.builder("circuit.breaker.state.open")
                .description("Number of times circuit breaker opened").register(meterRegistry);

        circuitBreakerClosedCounter = Counter.builder("circuit.breaker.state.closed")
                .description("Number of times circuit breaker closed").register(meterRegistry);

        circuitBreakerHalfOpenCounter = Counter.builder("circuit.breaker.state.half_open")
                .description("Number of times circuit breaker half opened").register(meterRegistry);

        // Счетчики для вызовов
        circuitBreakerCallNotPermittedCounter =
                Counter.builder("circuit.breaker.calls.not_permitted")
                        .description("Number of calls not permitted due to circuit breaker")
                        .register(meterRegistry);

        circuitBreakerSuccessCounter = Counter.builder("circuit.breaker.calls.success")
                .description("Number of successful calls").register(meterRegistry);

        circuitBreakerFailureCounter = Counter.builder("circuit.breaker.calls.failure")
                .description("Number of failed calls").register(meterRegistry);

        // Gauge для текущего состояния
        Gauge.builder("circuit.breaker.state.current", this,
                service -> service.getCurrentStateValue())
                .description("Current circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(meterRegistry);

        // Gauge для метрик
        Gauge.builder("circuit.breaker.metrics.failure_rate", this,
                service -> service.getFailureRate()).description("Circuit breaker failure rate")
                .register(meterRegistry);

        Gauge.builder("circuit.breaker.metrics.success_rate", this,
                service -> service.getSuccessRate()).description("Circuit breaker success rate")
                .register(meterRegistry);

        Gauge.builder("circuit.breaker.metrics.number_of_calls", this,
                service -> service.getNumberOfCalls()).description("Total number of calls")
                .register(meterRegistry);

        Gauge.builder("circuit.breaker.metrics.number_of_successful_calls", this,
                service -> service.getNumberOfSuccessfulCalls())
                .description("Number of successful calls").register(meterRegistry);

        Gauge.builder("circuit.breaker.metrics.number_of_failed_calls", this,
                service -> service.getNumberOfFailedCalls()).description("Number of failed calls")
                .register(meterRegistry);

        // Регистрация event listeners
        registerEventListeners();
    }

    /**
     * Получение статистики Circuit Breaker
     */
    public Map<String, Object> getCircuitBreakerStats() {
        Map<String, Object> stats = new HashMap<>();

        CircuitBreaker circuitBreaker = tinvestApiClient.getCircuitBreakerState() != null
                ? circuitBreakerRegistry.circuitBreaker("tinvest-api")
                : null;

        if (circuitBreaker != null) {
            CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();

            stats.put("state", circuitBreaker.getState().name());
            stats.put("failureRate", metrics.getFailureRate());
            stats.put("successRate", 1.0 - metrics.getFailureRate());
            stats.put("numberOfCalls", metrics.getNumberOfBufferedCalls());
            stats.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
            stats.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
            stats.put("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls());
            stats.put("averageResponseTime", 0.0); // Метрика недоступна в текущей версии
        } else {
            stats.put("state", "NOT_AVAILABLE");
            stats.put("failureRate", 0.0);
            stats.put("successRate", 0.0);
            stats.put("numberOfCalls", 0);
            stats.put("numberOfSuccessfulCalls", 0);
            stats.put("numberOfFailedCalls", 0);
            stats.put("numberOfNotPermittedCalls", 0);
            stats.put("averageResponseTime", 0.0);
        }

        return stats;
    }

    /**
     * Проверка здоровья Circuit Breaker
     */
    public boolean isHealthy() {
        CircuitBreaker.State state = tinvestApiClient.getCircuitBreakerState();
        return state == CircuitBreaker.State.CLOSED || state == CircuitBreaker.State.HALF_OPEN;
    }

    /**
     * Получение детальной информации о Circuit Breaker
     */
    public Map<String, Object> getDetailedStats() {
        Map<String, Object> stats = getCircuitBreakerStats();

        // Добавляем дополнительную информацию
        stats.put("isHealthy", isHealthy());
        stats.put("timestamp", System.currentTimeMillis());

        return stats;
    }

    // Методы для Gauge метрик

    private double getCurrentStateValue() {
        CircuitBreaker.State state = tinvestApiClient.getCircuitBreakerState();
        switch (state) {
            case CLOSED:
                return 0.0;
            case OPEN:
                return 1.0;
            case HALF_OPEN:
                return 2.0;
            case DISABLED:
                return -1.0;
            case FORCED_OPEN:
                return -1.0;
            case METRICS_ONLY:
                return -1.0;
            default:
                return -1.0;
        }
    }

    private double getFailureRate() {
        CircuitBreaker.Metrics metrics = tinvestApiClient.getCircuitBreakerMetrics();
        return metrics != null ? metrics.getFailureRate() : 0.0;
    }

    private double getSuccessRate() {
        CircuitBreaker.Metrics metrics = tinvestApiClient.getCircuitBreakerMetrics();
        return metrics != null ? (1.0 - metrics.getFailureRate()) : 0.0;
    }

    private double getNumberOfCalls() {
        CircuitBreaker.Metrics metrics = tinvestApiClient.getCircuitBreakerMetrics();
        return metrics != null ? metrics.getNumberOfBufferedCalls() : 0.0;
    }

    private double getNumberOfSuccessfulCalls() {
        CircuitBreaker.Metrics metrics = tinvestApiClient.getCircuitBreakerMetrics();
        return metrics != null ? metrics.getNumberOfSuccessfulCalls() : 0.0;
    }

    private double getNumberOfFailedCalls() {
        CircuitBreaker.Metrics metrics = tinvestApiClient.getCircuitBreakerMetrics();
        return metrics != null ? metrics.getNumberOfFailedCalls() : 0.0;
    }

    /**
     * Регистрация event listeners для Circuit Breaker
     */
    private void registerEventListeners() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest-api");

        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            log.info("Circuit breaker state transition: {} -> {}",
                    event.getStateTransition().getFromState(),
                    event.getStateTransition().getToState());

            // Обновляем счетчики состояний
            switch (event.getStateTransition().getToState()) {
                case OPEN -> circuitBreakerOpenCounter.increment();
                case CLOSED -> circuitBreakerClosedCounter.increment();
                case HALF_OPEN -> circuitBreakerHalfOpenCounter.increment();
            }
        }).onCallNotPermitted(event -> {
            log.warn("Circuit breaker call not permitted: {}", event.getEventType());
            circuitBreakerCallNotPermittedCounter.increment();
        }).onSuccess(event -> {
            log.debug("Circuit breaker call successful");
            circuitBreakerSuccessCounter.increment();
        }).onError(event -> {
            log.warn("Circuit breaker call failed: {}", event.getThrowable().getMessage());
            circuitBreakerFailureCounter.increment();
        });
    }
}
