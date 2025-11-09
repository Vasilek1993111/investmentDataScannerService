package com.example.investmentdatascannerservice.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;

/**
 * Конфигурация Resilience4j для Circuit Breaker, Retry и TimeLimiter
 * 
 * Обеспечивает отказоустойчивость при работе с внешними сервисами, автоматическое восстановление и
 * защиту от каскадных сбоев.
 */
@Slf4j
@Configuration
public class ResilienceConfig {

    /**
     * Конфигурация Circuit Breaker для T-Invest API
     */
    @Bean
    public CircuitBreakerConfig tinvestApiCircuitBreakerConfig() {
        log.info("Configuring Circuit Breaker for T-Invest API: failureRateThreshold=50%, waitDurationInOpenState=30s, slidingWindowSize=10, minimumNumberOfCalls=5");
        return CircuitBreakerConfig.custom()
                // Порог ошибок для перехода в OPEN состояние (50%)
                .failureRateThreshold(50)
                // Время ожидания в OPEN состоянии перед переходом в HALF_OPEN (30 секунд)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                // Размер скользящего окна для подсчета ошибок (10 запросов)
                .slidingWindowSize(10)
                // Минимальное количество вызовов для расчета статистики (5 запросов)
                .minimumNumberOfCalls(5)
                // Количество разрешенных вызовов в HALF_OPEN состоянии (3 запроса)
                .permittedNumberOfCallsInHalfOpenState(3)
                // Автоматический переход из OPEN в HALF_OPEN
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // Счетчик медленных вызовов (вызовы > 5 секунд)
                .slowCallRateThreshold(50).slowCallDurationThreshold(Duration.ofSeconds(5))
                // Игнорировать определенные исключения
                .ignoreExceptions(IllegalArgumentException.class).build();
    }

    /**
     * Реестр Circuit Breaker
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry(
            CircuitBreakerConfig tinvestApiCircuitBreakerConfig) {
        log.info("Creating CircuitBreakerRegistry");
        return CircuitBreakerRegistry.of(tinvestApiCircuitBreakerConfig);
    }

    /**
     * Circuit Breaker для T-Invest API
     */
    @Bean
    public CircuitBreaker tinvestApiCircuitBreaker(CircuitBreakerRegistry circuitBreakerRegistry) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("tinvest-api");
        log.info("Circuit Breaker 'tinvest-api' created and registered");
        return circuitBreaker;
    }

    /**
     * Конфигурация Retry для T-Invest API
     */
    @Bean
    public RetryConfig tinvestApiRetryConfig() {
        log.info("Configuring Retry for T-Invest API: maxAttempts=3, waitDuration=1s");
        return RetryConfig.custom()
                // Максимальное количество попыток (3)
                .maxAttempts(3)
                // Задержка между попытками (1 секунда)
                .waitDuration(Duration.ofSeconds(1))
                // Экспоненциальная задержка
                .retryOnException(throwable -> {
                    // Повторять только при сетевых ошибках
                    return throwable instanceof java.net.ConnectException
                            || throwable instanceof java.net.SocketTimeoutException
                            || throwable instanceof java.util.concurrent.TimeoutException;
                })
                // Игнорировать определенные исключения
                .ignoreExceptions(IllegalArgumentException.class).build();
    }

    /**
     * Реестр Retry
     */
    @Bean
    public RetryRegistry retryRegistry(RetryConfig tinvestApiRetryConfig) {
        log.info("Creating RetryRegistry");
        return RetryRegistry.of(tinvestApiRetryConfig);
    }

    /**
     * Retry для T-Invest API
     */
    @Bean
    public Retry tinvestApiRetry(RetryRegistry retryRegistry) {
        Retry retry = retryRegistry.retry("tinvest-api");
        log.info("Retry 'tinvest-api' created and registered");
        return retry;
    }

    /**
     * Конфигурация TimeLimiter для T-Invest API
     */
    @Bean
    public TimeLimiterConfig tinvestApiTimeLimiterConfig() {
        log.info("Configuring TimeLimiter for T-Invest API: timeoutDuration=10s, cancelRunningFuture=true");
        return TimeLimiterConfig.custom()
                // Таймаут для вызовов (10 секунд)
                .timeoutDuration(Duration.ofSeconds(10))
                // Отменить выполнение при таймауте
                .cancelRunningFuture(true).build();
    }
}
