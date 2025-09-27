package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для загрузки всех инструментов при запуске приложения
 * 
 * Автоматически загружает акции, фьючерсы и индикативы в кэш при старте приложения для быстрого
 * доступа во время работы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentStartupLoader {

    private final InstrumentCacheService instrumentCacheService;
    private final PriceCacheService priceCacheService;
    private final StartupPriceLoader startupPriceLoader;
    private final TodayVolumeService todayVolumeService;
    private final HistoryVolumeService historyVolumeService;

    /**
     * Загрузка всех инструментов при готовности приложения
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void loadInstrumentsOnStartup() {
        log.info("Starting instrument loading on application startup...");

        try {
            // Загружаем инструменты асинхронно
            CompletableFuture<Void> instrumentsFuture =
                    CompletableFuture.runAsync(this::loadAllInstruments);
            CompletableFuture<Void> pricesFuture =
                    CompletableFuture.runAsync(() -> startupPriceLoader.loadPricesOnStartup());
            CompletableFuture<Void> todayVolumeFuture =
                    CompletableFuture.runAsync(() -> todayVolumeService.loadTodayVolumeData());
            CompletableFuture<Void> historyVolumeFuture =
                    CompletableFuture.runAsync(() -> historyVolumeService.loadHistoryVolumeData());

            // Ждем завершения всех задач
            CompletableFuture
                    .allOf(instrumentsFuture, pricesFuture, todayVolumeFuture, historyVolumeFuture)
                    .join();

            log.info("Instrument loading completed successfully on startup");
            logInstrumentStats();

        } catch (Exception e) {
            log.error("Error during startup instrument loading", e);
        }
    }

    /**
     * Загрузка всех инструментов в кэш
     */
    private void loadAllInstruments() {
        try {
            log.info("Loading all instruments...");

            // Инициализируем кэш инструментов
            instrumentCacheService.initializeCache();

            // Получаем все инструменты для сканирования
            List<String> allInstruments = instrumentCacheService.getInstrumentsForScanning();

            log.info("Loaded {} instruments into cache", allInstruments.size());
            log.info("First 10 instruments: {}",
                    allInstruments.subList(0, Math.min(10, allInstruments.size())));

            // Загружаем цены для всех инструментов из кэша
            loadPricesForAllInstruments(allInstruments);

        } catch (Exception e) {
            log.error("Error loading instruments", e);
        }
    }

    /**
     * Загрузка цен для всех инструментов из кэша
     */
    private void loadPricesForAllInstruments(List<String> figis) {
        try {
            log.info("Loading prices for {} instruments from cache...", figis.size());

            // Получаем все цены из кэша
            Map<String, BigDecimal> allClosePrices = priceCacheService.getAllClosePrices();
            Map<String, BigDecimal> allEveningSessionPrices =
                    priceCacheService.getAllEveningSessionPrices();
            Map<String, BigDecimal> allOpenPrices = priceCacheService.getAllOpenPrices();

            // Фильтруем цены только для наших инструментов
            Map<String, BigDecimal> closePrices = figis.stream().filter(allClosePrices::containsKey)
                    .collect(Collectors.toMap(figi -> figi, allClosePrices::get));

            Map<String, BigDecimal> eveningSessionPrices =
                    figis.stream().filter(allEveningSessionPrices::containsKey)
                            .collect(Collectors.toMap(figi -> figi, allEveningSessionPrices::get));

            Map<String, BigDecimal> openPrices = figis.stream().filter(allOpenPrices::containsKey)
                    .collect(Collectors.toMap(figi -> figi, allOpenPrices::get));

            log.info("Loaded {} close prices from cache", closePrices.size());
            log.info("Loaded {} evening session prices from cache", eveningSessionPrices.size());
            log.info("Loaded {} open prices from cache", openPrices.size());

            // Обновляем кэш инструментов с загруженными ценами
            instrumentCacheService.loadClosePrices(closePrices);
            instrumentCacheService.loadOpenPrices(openPrices);

            log.info("Successfully loaded prices for all instruments from cache");

        } catch (Exception e) {
            log.error("Error loading prices for instruments from cache", e);
        }
    }


    /**
     * Принудительная перезагрузка всех инструментов
     */
    @Async
    public void reloadAllInstruments() {
        log.info("Starting manual instrument reload...");

        try {
            // Перезагружаем кэш инструментов
            instrumentCacheService.reloadCache();

            // Перезагружаем кэш цен
            priceCacheService.reloadCache();

            // Перезагружаем данные today_volume_view
            todayVolumeService.reloadTodayVolumeData();

            // Перезагружаем исторические данные
            historyVolumeService.reloadHistoryVolumeData();

            // Получаем все инструменты и загружаем для них цены
            List<String> allInstruments = instrumentCacheService.getInstrumentsForScanning();
            loadPricesForAllInstruments(allInstruments);

            log.info("Manual instrument reload completed successfully");
            logInstrumentStats();

        } catch (Exception e) {
            log.error("Error during manual instrument reload", e);
        }
    }

    /**
     * Перезагрузка только инструментов (без цен)
     */
    @Async
    public void reloadInstrumentsOnly() {
        log.info("Starting manual instruments-only reload...");

        try {
            // Перезагружаем только кэш инструментов
            instrumentCacheService.reloadCache();

            // Перезагружаем данные today_volume_view
            todayVolumeService.reloadTodayVolumeData();

            // Получаем все инструменты
            List<String> allInstruments = instrumentCacheService.getInstrumentsForScanning();

            log.info("Manual instruments-only reload completed successfully");
            log.info("Loaded {} instruments into cache", allInstruments.size());
            log.info("First 10 instruments: {}",
                    allInstruments.subList(0, Math.min(10, allInstruments.size())));

        } catch (Exception e) {
            log.error("Error during manual instruments-only reload", e);
        }
    }


    /**
     * Логирование статистики инструментов
     */
    private void logInstrumentStats() {
        try {
            List<String> allInstruments = instrumentCacheService.getInstrumentsForScanning();
            var priceStats = priceCacheService.getCacheStats();
            var todayVolumeStats = todayVolumeService.getTodayVolumeStats();
            var historyVolumeStats = historyVolumeService.getHistoryVolumeStats();

            log.info("=== INSTRUMENT LOADING STATISTICS ===");
            log.info("Total instruments loaded: {}", allInstruments.size());
            log.info("Close prices in cache: {}", priceStats.get("closePricesCount"));
            log.info("Evening session prices in cache: {}",
                    priceStats.get("eveningSessionPricesCount"));
            log.info("Open prices in cache: {}", priceStats.get("openPricesCount"));
            log.info("Last close price date: {}", priceStats.get("lastClosePriceDate"));
            log.info("Last evening session date: {}", priceStats.get("lastEveningSessionDate"));
            log.info("Last open price date: {}", priceStats.get("lastOpenPriceDate"));

            // Показываем покрытие цен по инструментам
            int instrumentsWithClosePrices = (int) priceStats.get("closePricesCount");
            int instrumentsWithEveningPrices = (int) priceStats.get("eveningSessionPricesCount");
            int instrumentsWithOpenPrices = (int) priceStats.get("openPricesCount");

            log.info("Price coverage: {}/{} close, {}/{} evening, {}/{} open",
                    instrumentsWithClosePrices, allInstruments.size(), instrumentsWithEveningPrices,
                    allInstruments.size(), instrumentsWithOpenPrices, allInstruments.size());

            // Показываем статистику today_volume_view
            log.info("=== TODAY VOLUME VIEW STATISTICS ===");
            log.info("Total instruments with volume data: {}",
                    todayVolumeStats.get("totalInstruments"));
            log.info("Instruments with weekend exchange volume: {}",
                    todayVolumeStats.get("instrumentsWithWeekendExchangeVolume"));
            log.info("Total weekend exchange volume: {}",
                    todayVolumeStats.get("totalWeekendExchangeVolume"));
            log.info("Total weekend exchange candles: {}",
                    todayVolumeStats.get("totalWeekendExchangeCandles"));
            log.info("Average weekend exchange volume: {}",
                    todayVolumeStats.get("avgWeekendExchangeVolume"));

            // Показываем статистику исторических данных
            log.info("=== HISTORY VOLUME AGGREGATION STATISTICS ===");
            log.info("Total instruments with history: {}",
                    historyVolumeStats.get("totalInstruments"));
            log.info("Instruments with total volume: {}",
                    historyVolumeStats.get("instrumentsWithTotalVolume"));
            log.info("Instruments with morning volume: {}",
                    historyVolumeStats.get("instrumentsWithMorningVolume"));
            log.info("Instruments with main volume: {}",
                    historyVolumeStats.get("instrumentsWithMainVolume"));
            log.info("Instruments with evening volume: {}",
                    historyVolumeStats.get("instrumentsWithEveningVolume"));
            log.info("Instruments with weekend exchange volume: {}",
                    historyVolumeStats.get("instrumentsWithWeekendExchangeVolume"));
            log.info("Instruments with weekend OTC volume: {}",
                    historyVolumeStats.get("instrumentsWithWeekendOtcVolume"));
            log.info("Total historical volume: {}", historyVolumeStats.get("totalVolume"));
            log.info("=====================================");

        } catch (Exception e) {
            log.error("Error getting instrument statistics", e);
        }
    }

}
