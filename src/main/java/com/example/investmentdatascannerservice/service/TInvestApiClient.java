package com.example.investmentdatascannerservice.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.tinkoff.piapi.contract.v1.Etf;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Share;

/**
 * Клиент для работы с T-Invest API с поддержкой Circuit Breaker
 * 
 * Обеспечивает отказоустойчивость при работе с внешним API, автоматическое восстановление и
 * fallback механизмы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TInvestApiClient {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    /**
     * Получение последних цен с Circuit Breaker
     */
    public List<LastPrice> getLastPrices(List<String> figis) {
        return Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Fetching last prices for {} instruments", figis.size());
                    // Здесь должен быть реальный вызов API
                    return getCachedLastPrices(figis);
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using cached data for last prices");
                    return getCachedLastPrices(figis);
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using cached data for last prices");
                    return getCachedLastPrices(figis);
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using cached data for last prices: {}",
                            ex.getMessage());
                    return getCachedLastPrices(figis);
                }).get();
    }

    /**
     * Получение информации об инструментах с Circuit Breaker
     */
    public List<Share> getShares() {
        return Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Fetching shares information");
                    return getCachedShares();
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using cached shares data");
                    return getCachedShares();
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using cached shares data");
                    return getCachedShares();
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using cached shares data: {}", ex.getMessage());
                    return getCachedShares();
                }).get();
    }

    /**
     * Получение информации об индексах с Circuit Breaker
     */
    public List<Etf> getEtfs() {
        return Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Fetching ETFs information");
                    return getCachedEtfs();
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using cached ETFs data");
                    return getCachedEtfs();
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using cached ETFs data");
                    return getCachedEtfs();
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using cached ETFs data: {}", ex.getMessage());
                    return getCachedEtfs();
                }).get();
    }

    /**
     * Асинхронное получение последних цен с Circuit Breaker
     */
    public CompletableFuture<List<LastPrice>> getLastPricesAsync(List<String> figis) {
        return Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Fetching last prices asynchronously for {} instruments",
                            figis.size());
                    return CompletableFuture.completedFuture(getCachedLastPrices(figis));
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using cached data for async last prices");
                    return CompletableFuture.completedFuture(getCachedLastPrices(figis));
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using cached data for async last prices");
                    return CompletableFuture.completedFuture(getCachedLastPrices(figis));
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using cached data for async last prices: {}",
                            ex.getMessage());
                    return CompletableFuture.completedFuture(getCachedLastPrices(figis));
                }).get();
    }

    /**
     * Проверка доступности API
     */
    public boolean isApiAvailable() {
        try {
            // Простая проверка доступности
            return circuitBreaker.getState() == CircuitBreaker.State.CLOSED;
        } catch (Exception e) {
            log.debug("API availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Получение состояния Circuit Breaker
     */
    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }

    /**
     * Получение метрик Circuit Breaker
     */
    public CircuitBreaker.Metrics getCircuitBreakerMetrics() {
        return circuitBreaker.getMetrics();
    }

    // Fallback методы для получения кэшированных данных

    private List<LastPrice> getCachedLastPrices(List<String> figis) {
        log.debug("Using cached data for {} instruments", figis.size());
        // Здесь должна быть логика получения кэшированных данных
        // Пока возвращаем пустой список
        return List.of();
    }

    private List<Share> getCachedShares() {
        log.debug("Using cached shares data");
        // Здесь должна быть логика получения кэшированных данных
        return List.of();
    }

    private List<Etf> getCachedEtfs() {
        log.debug("Using cached ETFs data");
        // Здесь должна быть логика получения кэшированных данных
        return List.of();
    }
}
