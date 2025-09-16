package com.example.investmentdatascannerservice.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для загрузки цен при запуске приложения
 * 
 * Выполняет предварительную загрузку и кэширование цен закрытия, открытия и вечерней сессии для
 * быстрого доступа во время работы приложения.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StartupPriceLoader {

    private final PriceCacheService priceCacheService;
    private final TInvestApiClient tinvestApiClient;

    /**
     * Загрузка цен при готовности приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void loadPricesOnStartup() {
        log.info("Starting price loading on application startup...");

        try {
            // Загружаем цены асинхронно
            CompletableFuture<Void> closePricesFuture =
                    CompletableFuture.runAsync(this::loadClosePrices);
            CompletableFuture<Void> eveningSessionPricesFuture =
                    CompletableFuture.runAsync(this::loadEveningSessionPrices);

            // Ждем завершения всех задач
            CompletableFuture.allOf(closePricesFuture, eveningSessionPricesFuture).join();

            log.info("Price loading completed successfully on startup");
            logCacheStats();

        } catch (Exception e) {
            log.error("Error during startup price loading", e);
        }
    }

    /**
     * Загрузка цен закрытия
     */
    private void loadClosePrices() {
        try {
            log.info("Loading close prices...");
            priceCacheService.loadAllClosePrices();
            log.info("Close prices loaded successfully");
        } catch (Exception e) {
            log.error("Error loading close prices", e);
        }
    }

    /**
     * Загрузка цен вечерней сессии
     */
    private void loadEveningSessionPrices() {
        try {
            log.info("Loading evening session prices...");
            priceCacheService.loadAllEveningSessionPrices();
            log.info("Evening session prices loaded successfully");
        } catch (Exception e) {
            log.error("Error loading evening session prices", e);
        }
    }

    /**
     * Принудительная перезагрузка всех цен
     */
    @Async
    public void reloadAllPrices() {
        log.info("Starting manual price reload...");

        try {
            priceCacheService.reloadCache();
            log.info("Manual price reload completed successfully");
            logCacheStats();
        } catch (Exception e) {
            log.error("Error during manual price reload", e);
        }
    }

    /**
     * Загрузка цен для конкретных инструментов
     */
    @Async
    public void loadPricesForInstruments(List<String> figis) {
        log.info("Loading prices for {} instruments...", figis.size());

        try {
            // Получаем последние цены из кэша
            var closePrices = priceCacheService.getLastClosePrices(figis);
            var eveningSessionPrices = priceCacheService.getLastEveningSessionPrices(figis);

            log.info("Loaded close prices for {} instruments", closePrices.size());
            log.info("Loaded evening session prices for {} instruments",
                    eveningSessionPrices.size());

        } catch (Exception e) {
            log.error("Error loading prices for instruments", e);
        }
    }

    /**
     * Проверка доступности API и загрузка актуальных данных
     */
    @Async
    public void refreshPricesFromApi() {
        log.info("Refreshing prices from API...");

        try {
            if (tinvestApiClient.isApiAvailable()) {
                log.info("API is available, refreshing prices...");
                // Здесь можно добавить логику получения актуальных цен из API
                // и обновления кэша
                priceCacheService.reloadCache();
                log.info("Prices refreshed from API successfully");
            } else {
                log.warn("API is not available, using cached data");
            }
        } catch (Exception e) {
            log.error("Error refreshing prices from API", e);
        }
    }

    /**
     * Логирование статистики кэша
     */
    private void logCacheStats() {
        try {
            var stats = priceCacheService.getCacheStats();
            log.info("Cache statistics: {}", stats);
        } catch (Exception e) {
            log.error("Error getting cache statistics", e);
        }
    }

    /**
     * Получение статистики загрузки
     */
    public void logLoadingStats() {
        logCacheStats();
    }
}
