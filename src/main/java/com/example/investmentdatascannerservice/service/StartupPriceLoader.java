package com.example.investmentdatascannerservice.service;

import java.util.concurrent.CompletableFuture;
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

    /**
     * Загрузка цен при готовности приложения
     */
    @Async
    public void loadPricesOnStartup() {
        log.info("Starting price loading on application startup...");

        try {
            // Загружаем цены асинхронно
            CompletableFuture<Void> closePricesFuture =
                    CompletableFuture.runAsync(this::loadClosePrices);
            CompletableFuture<Void> eveningSessionPricesFuture =
                    CompletableFuture.runAsync(this::loadEveningSessionPrices);
            CompletableFuture<Void> openPricesFuture =
                    CompletableFuture.runAsync(this::loadOpenPrices);

            // Ждем завершения всех задач
            CompletableFuture.allOf(closePricesFuture, eveningSessionPricesFuture, openPricesFuture)
                    .join();

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
     * Загрузка цен открытия
     */
    private void loadOpenPrices() {
        try {
            log.info("Loading open prices...");
            priceCacheService.loadAllOpenPrices();
            log.info("Open prices loaded successfully");
        } catch (Exception e) {
            log.error("Error loading open prices", e);
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

}
