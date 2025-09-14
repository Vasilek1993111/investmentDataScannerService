package com.example.investmentdatascannerservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.OrderBook;
import ru.tinkoff.piapi.contract.v1.Trade;

/**
 * Тесты для Circuit Breaker функциональности
 */
@ExtendWith(MockitoExtension.class)
class CircuitBreakerTest {

    @Mock
    private QuoteScannerService quoteScannerService;

    @Mock
    private TInvestApiClient tinvestApiClient;

    @Mock
    private CircuitBreakerMonitoringService monitoringService;

    @Mock
    private InstrumentCacheService instrumentCacheService;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private ResilientQuoteScannerService resilientService;

    @BeforeEach
    void setUp() {
        // Создаем конфигурацию Circuit Breaker для тестов
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50).waitDurationInOpenState(Duration.ofSeconds(1))
                .slidingWindowSize(5).minimumNumberOfCalls(2).build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(circuitBreakerConfig);

        RetryConfig retryConfig =
                RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(100)).build();

        retryRegistry = RetryRegistry.of(retryConfig);

        resilientService = new ResilientQuoteScannerService(quoteScannerService,
                circuitBreakerRegistry.circuitBreaker("test"), retryRegistry.retry("test"),
                tinvestApiClient, monitoringService);
    }

    @Test
    void testSuccessfulProcessing() {
        // Given
        LastPrice price = LastPrice.newBuilder().setFigi("test-figi")
                .setPrice(ru.tinkoff.piapi.contract.v1.Quotation.newBuilder().setUnits(100).build())
                .build();

        // When
        resilientService.processLastPrice(price);

        // Then
        verify(quoteScannerService, times(1)).processLastPrice(price);
    }

    @Test
    void testCircuitBreakerOpenState() {
        // Given
        LastPrice price = LastPrice.newBuilder().setFigi("test-figi")
                .setPrice(ru.tinkoff.piapi.contract.v1.Quotation.newBuilder().setUnits(100).build())
                .build();

        // Simulate circuit breaker open state
        when(tinvestApiClient.getCircuitBreakerState()).thenReturn(CircuitBreaker.State.OPEN);
        when(monitoringService.isHealthy()).thenReturn(false);

        // When
        resilientService.processLastPrice(price);

        // Then
        // Should still call the service (fallback will be triggered internally)
        verify(quoteScannerService, times(1)).processLastPrice(price);
    }

    @Test
    void testHealthCheck() {
        // Given
        when(monitoringService.isHealthy()).thenReturn(true);

        // When
        boolean isHealthy = resilientService.isHealthy();

        // Then
        assertTrue(isHealthy);
        verify(monitoringService, times(1)).isHealthy();
    }

    @Test
    void testCircuitBreakerState() {
        // Given
        when(tinvestApiClient.getCircuitBreakerState()).thenReturn(CircuitBreaker.State.CLOSED);

        // When
        String state = resilientService.getCircuitBreakerState();

        // Then
        assertEquals("CLOSED", state);
        verify(tinvestApiClient, times(1)).getCircuitBreakerState();
    }

    @Test
    void testStatsFallback() {
        // Given
        when(tinvestApiClient.getCircuitBreakerState()).thenReturn(CircuitBreaker.State.OPEN);
        when(monitoringService.isHealthy()).thenReturn(false);

        // When
        Object stats = resilientService.getStats();

        // Then
        assertNotNull(stats);
        // In fallback mode, stats should contain fallback information
        assertTrue(stats.toString().contains("FALLBACK_MODE"));
    }

    @Test
    void testTradeProcessing() {
        // Given
        Trade trade = Trade.newBuilder().setFigi("test-figi")
                .setPrice(ru.tinkoff.piapi.contract.v1.Quotation.newBuilder().setUnits(100).build())
                .setQuantity(10).build();

        // When
        resilientService.processTrade(trade);

        // Then
        verify(quoteScannerService, times(1)).processTrade(trade);
    }

    @Test
    void testOrderBookProcessing() {
        // Given
        OrderBook orderBook = OrderBook.newBuilder().setFigi("test-figi").setDepth(1).build();

        // When
        resilientService.processOrderBook(orderBook);

        // Then
        verify(quoteScannerService, times(1)).processOrderBook(orderBook);
    }
}
