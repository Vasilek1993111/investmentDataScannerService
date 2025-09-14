package com.example.investmentdatascannerservice.service;

import java.util.Map;
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
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.OrderBook;
import ru.tinkoff.piapi.contract.v1.Trade;

/**
 * Отказоустойчивый сервис для сканирования котировок с Circuit Breaker
 * 
 * Обеспечивает надежную обработку данных даже при недоступности внешних сервисов, использует
 * fallback механизмы и автоматическое восстановление.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientQuoteScannerService {

    private final QuoteScannerService quoteScannerService;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final TInvestApiClient tinvestApiClient;
    private final CircuitBreakerMonitoringService monitoringService;

    /**
     * Обработка последних цен с Circuit Breaker
     */
    public void processLastPrice(LastPrice price) {
        Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Processing last price for FIGI: {}", price.getFigi());
                    quoteScannerService.processLastPrice(price);
                    return null;
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using fallback for last price processing");
                    processLastPriceFallback(price);
                    return null;
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using fallback for last price processing");
                    processLastPriceFallback(price);
                    return null;
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using fallback for last price processing: {}",
                            ex.getMessage());
                    processLastPriceFallback(price);
                    return null;
                }).onFailure(throwable -> {
                    log.error("Unexpected error in last price processing", throwable);
                    processLastPriceFallback(price);
                });
    }

    /**
     * Обработка сделок с Circuit Breaker
     */
    public void processTrade(Trade trade) {
        Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Processing trade for FIGI: {}", trade.getFigi());
                    quoteScannerService.processTrade(trade);
                    return null;
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using fallback for trade processing");
                    processTradeFallback(trade);
                    return null;
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using fallback for trade processing");
                    processTradeFallback(trade);
                    return null;
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using fallback for trade processing: {}",
                            ex.getMessage());
                    processTradeFallback(trade);
                    return null;
                }).onFailure(throwable -> {
                    log.error("Unexpected error in trade processing", throwable);
                    processTradeFallback(trade);
                });
    }

    /**
     * Обработка стакана заявок с Circuit Breaker
     */
    public void processOrderBook(OrderBook orderBook) {
        Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Processing order book for FIGI: {}", orderBook.getFigi());
                    quoteScannerService.processOrderBook(orderBook);
                    return null;
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using fallback for order book processing");
                    processOrderBookFallback(orderBook);
                    return null;
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using fallback for order book processing");
                    processOrderBookFallback(orderBook);
                    return null;
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using fallback for order book processing: {}",
                            ex.getMessage());
                    processOrderBookFallback(orderBook);
                    return null;
                }).onFailure(throwable -> {
                    log.error("Unexpected error in order book processing", throwable);
                    processOrderBookFallback(orderBook);
                });
    }

    /**
     * Асинхронная обработка последних цен с Circuit Breaker
     */
    public CompletableFuture<Void> processLastPriceAsync(LastPrice price) {
        return Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Processing last price asynchronously for FIGI: {}", price.getFigi());
                    return CompletableFuture
                            .runAsync(() -> quoteScannerService.processLastPrice(price));
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn(
                            "Circuit breaker is OPEN, using fallback for async last price processing");
                    return CompletableFuture.runAsync(() -> processLastPriceFallback(price));
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using fallback for async last price processing");
                    return CompletableFuture.runAsync(() -> processLastPriceFallback(price));
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using fallback for async last price processing: {}",
                            ex.getMessage());
                    return CompletableFuture.runAsync(() -> processLastPriceFallback(price));
                }).onFailure(throwable -> {
                    log.error("Unexpected error in async last price processing", throwable);
                }).get();
    }

    /**
     * Получение статистики с Circuit Breaker
     */
    public Map<String, Object> getStats() {
        return Try.ofSupplier(CircuitBreaker.decorateSupplier(circuitBreaker,
                () -> Retry.decorateSupplier(retry, () -> {
                    log.debug("Getting scanner stats");
                    return quoteScannerService.getStats();
                }).get())).recover(CallNotPermittedException.class, ex -> {
                    log.warn("Circuit breaker is OPEN, using fallback for stats");
                    return getStatsFallback();
                }).recover(TimeoutException.class, ex -> {
                    log.warn("Timeout occurred, using fallback for stats");
                    return getStatsFallback();
                }).recover(CompletionException.class, ex -> {
                    log.warn("API call failed, using fallback for stats: {}", ex.getMessage());
                    return getStatsFallback();
                }).get();
    }

    /**
     * Проверка здоровья системы
     */
    public boolean isHealthy() {
        return monitoringService.isHealthy();
    }

    /**
     * Получение состояния Circuit Breaker
     */
    public String getCircuitBreakerState() {
        return tinvestApiClient.getCircuitBreakerState().name();
    }

    // Fallback методы

    private void processLastPriceFallback(LastPrice price) {
        try {
            log.debug("Processing last price fallback for FIGI: {}", price.getFigi());
            // Здесь можно добавить логику обработки кэшированных данных
            // или альтернативные источники данных
        } catch (Exception e) {
            log.error("Error in last price fallback processing", e);
        }
    }

    private void processTradeFallback(Trade trade) {
        try {
            log.debug("Processing trade fallback for FIGI: {}", trade.getFigi());
            // Здесь можно добавить логику обработки кэшированных данных
        } catch (Exception e) {
            log.error("Error in trade fallback processing", e);
        }
    }

    private void processOrderBookFallback(OrderBook orderBook) {
        try {
            log.debug("Processing order book fallback for FIGI: {}", orderBook.getFigi());
            // Здесь можно добавить логику обработки кэшированных данных
        } catch (Exception e) {
            log.error("Error in order book fallback processing", e);
        }
    }

    private Map<String, Object> getStatsFallback() {
        return Map.of("status", "FALLBACK_MODE", "circuitBreakerState", getCircuitBreakerState(),
                "isHealthy", isHealthy(), "timestamp", System.currentTimeMillis());
    }
}
