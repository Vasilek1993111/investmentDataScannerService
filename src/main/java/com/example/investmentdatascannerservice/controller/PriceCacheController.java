package com.example.investmentdatascannerservice.controller;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.service.HistoryVolumeService;
import com.example.investmentdatascannerservice.service.InstrumentStartupLoader;
import com.example.investmentdatascannerservice.service.PriceCacheService;
import com.example.investmentdatascannerservice.service.StartupPriceLoader;
import com.example.investmentdatascannerservice.service.TodayVolumeService;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Контроллер для управления кэшем цен
 * 
 * Предоставляет REST API для управления кэшированием цен закрытия, открытия и вечерней сессии.
 */
@Slf4j
@RestController
@RequestMapping("/api/price-cache")
@RequiredArgsConstructor
public class PriceCacheController {

    private final PriceCacheService priceCacheService;
    private final StartupPriceLoader startupPriceLoader;
    private final InstrumentStartupLoader instrumentStartupLoader;
    private final TodayVolumeService todayVolumeService;
    private final HistoryVolumeService historyVolumeService;
    private final InstrumentCacheService instrumentCacheService;

    /**
     * Получение статистики кэша
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        try {
            Map<String, Object> stats = priceCacheService.getCacheStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting cache stats", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение всех цен закрытия из кэша
     */
    @GetMapping("/close-price")
    public ResponseEntity<Map<String, Object>> getClosePrice() {
        try {
            Map<String, BigDecimal> allClosePrices = priceCacheService.getAllClosePrices();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("prices", allClosePrices);
            result.put("date", priceCacheService.getLastClosePriceDate());
            result.put("count", allClosePrices.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting all close prices from cache", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение всех цен открытия из кэша
     */
    @GetMapping("/open-price")
    public ResponseEntity<Map<String, Object>> getOpenPrice() {
        try {
            Map<String, BigDecimal> allOpenPrices = priceCacheService.getAllOpenPrices();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("prices", allOpenPrices);
            result.put("date", priceCacheService.getLastOpenPriceDate());
            result.put("count", allOpenPrices.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting all open prices from cache", e);
            return ResponseEntity.internalServerError().build();
        }
    }


    /**
     * Получение всех цен вечерней сессии из кэша
     */
    @GetMapping("/evening-session-price")
    public ResponseEntity<Map<String, Object>> getEveningSessionPrice() {
        try {
            Map<String, BigDecimal> allEveningSessionPrices =
                    priceCacheService.getAllEveningSessionPrices();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("prices", allEveningSessionPrices);
            result.put("date", priceCacheService.getLastEveningSessionPriceDate());
            result.put("count", allEveningSessionPrices.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting all evening session prices from cache", e);
            return ResponseEntity.internalServerError().build();
        }
    }



    /**
     * Очистка кэша
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearCache() {
        try {
            log.info("Manual cache clear requested");
            priceCacheService.clearCache();
            return ResponseEntity.ok("Cache cleared successfully");
        } catch (Exception e) {
            log.error("Error clearing cache", e);
            return ResponseEntity.internalServerError()
                    .body("Error clearing cache: " + e.getMessage());
        }
    }

    /**
     * Асинхронная перезагрузка всех цен
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadAllPrices() {
        try {
            log.info("Manual reload all prices requested");
            startupPriceLoader.reloadAllPrices();
            return ResponseEntity.ok("All prices reload started");
        } catch (Exception e) {
            log.error("Error reloading all prices", e);
            return ResponseEntity.internalServerError()
                    .body("Error reloading all prices: " + e.getMessage());
        }
    }



    /**
     * Перезагрузка всех инструментов (без цен)
     */
    @PostMapping("/reload-instruments")
    public ResponseEntity<String> reloadAllInstruments() {
        try {
            log.info("Manual reload all instruments requested");
            instrumentStartupLoader.reloadInstrumentsOnly();
            return ResponseEntity.ok("All instruments reload started");
        } catch (Exception e) {
            log.error("Error reloading all instruments", e);
            return ResponseEntity.internalServerError()
                    .body("Error reloading all instruments: " + e.getMessage());
        }
    }

    /**
     * Получение всех цен по FIGI из кэша
     */
    @GetMapping("/prices/{figi}")
    public ResponseEntity<Map<String, Object>> getPricesByFigi(@PathVariable String figi) {
        try {
            Map<String, BigDecimal> prices = priceCacheService.getPricesForFigi(figi);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("figi", figi);
            result.putAll(prices);
            result.put("dates", Map.of("closePriceDate", priceCacheService.getLastClosePriceDate(),
                    "eveningSessionPriceDate", priceCacheService.getLastEveningSessionPriceDate(),
                    "openPriceDate", priceCacheService.getLastOpenPriceDate()));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting prices for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение данных выходной биржевой сессии из today_volume_view
     */
    @GetMapping("/weekend-exchange-volume")
    public ResponseEntity<Map<String, Object>> getWeekendExchangeVolume() {
        try {
            Map<String, Long> volumes = todayVolumeService.getAllWeekendExchangeVolumes();
            Map<String, Long> candles = todayVolumeService.getAllWeekendExchangeCandles();
            Map<String, BigDecimal> avgVolumes =
                    todayVolumeService.getAllWeekendExchangeAvgVolumes();
            Map<String, Object> stats = todayVolumeService.getTodayVolumeStats();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("volumes", volumes);
            result.put("candles", candles);
            result.put("avgVolumes", avgVolumes);
            result.put("stats", stats);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting weekend exchange volume data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение данных выходной биржевой сессии для конкретного инструмента
     */
    @GetMapping("/weekend-exchange-volume/{figi}")
    public ResponseEntity<Map<String, Object>> getWeekendExchangeVolumeByFigi(
            @PathVariable String figi) {
        try {
            Map<String, Object> result = Map.of("figi", figi, "volume",
                    todayVolumeService.getWeekendExchangeVolume(figi), "candles",
                    todayVolumeService.getWeekendExchangeCandles(figi), "avgVolume",
                    todayVolumeService.getWeekendExchangeAvgVolume(figi));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting weekend exchange volume data for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Загрузка уже проторгованных объемов из today_volume_view в накопленные объемы
     */
    @PostMapping("/load-weekend-volumes")
    public ResponseEntity<String> loadWeekendVolumes() {
        try {
            log.info("Loading weekend exchange volumes into accumulated volumes...");
            instrumentCacheService.loadWeekendExchangeVolumes();
            return ResponseEntity.ok("Weekend exchange volumes loaded into accumulated volumes");
        } catch (Exception e) {
            log.error("Error loading weekend exchange volumes", e);
            return ResponseEntity.internalServerError()
                    .body("Error loading weekend exchange volumes: " + e.getMessage());
        }
    }

    /**
     * Очистка накопленных объемов (сброс счетчика)
     */
    @PostMapping("/clear-accumulated-volumes")
    public ResponseEntity<String> clearAccumulatedVolumes() {
        try {
            log.info("Clearing accumulated volumes...");
            instrumentCacheService.clearAccumulatedVolumes();
            return ResponseEntity.ok("Accumulated volumes cleared");
        } catch (Exception e) {
            log.error("Error clearing accumulated volumes", e);
            return ResponseEntity.internalServerError()
                    .body("Error clearing accumulated volumes: " + e.getMessage());
        }
    }

    /**
     * Получение исторических данных объемов из history_volume_aggregation
     */
    @GetMapping("/history-volumes")
    public ResponseEntity<Map<String, Object>> getHistoryVolumes() {
        try {
            Map<String, Long> totalVolumes = historyVolumeService.getAllTotalVolumes();
            Map<String, Long> morningVolumes = historyVolumeService.getAllMorningSessionVolumes();
            Map<String, Long> mainVolumes = historyVolumeService.getAllMainSessionVolumes();
            Map<String, Long> eveningVolumes = historyVolumeService.getAllEveningSessionVolumes();
            Map<String, Long> weekendExchangeVolumes =
                    historyVolumeService.getAllWeekendExchangeVolumes();
            Map<String, Long> weekendOtcVolumes = historyVolumeService.getAllWeekendOtcVolumes();
            Map<String, Object> stats = historyVolumeService.getHistoryVolumeStats();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("totalVolumes", totalVolumes);
            result.put("morningVolumes", morningVolumes);
            result.put("mainVolumes", mainVolumes);
            result.put("eveningVolumes", eveningVolumes);
            result.put("weekendExchangeVolumes", weekendExchangeVolumes);
            result.put("weekendOtcVolumes", weekendOtcVolumes);
            result.put("stats", stats);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history volume data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение исторических данных объемов для конкретного инструмента
     */
    @GetMapping("/history-volumes/{figi}")
    public ResponseEntity<Map<String, Object>> getHistoryVolumesByFigi(@PathVariable String figi) {
        try {
            Map<String, Object> result = Map.of("figi", figi, "totalVolume",
                    historyVolumeService.getTotalVolume(figi), "totalCandles",
                    historyVolumeService.getTotalCandles(figi), "avgVolumePerCandle",
                    historyVolumeService.getAvgVolumePerCandle(figi), "morningSessionVolume",
                    historyVolumeService.getMorningSessionVolume(figi), "mainSessionVolume",
                    historyVolumeService.getMainSessionVolume(figi), "eveningSessionVolume",
                    historyVolumeService.getEveningSessionVolume(figi), "weekendExchangeVolume",
                    historyVolumeService.getWeekendExchangeVolume(figi), "weekendOtcVolume",
                    historyVolumeService.getWeekendOtcVolume(figi));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history volume data for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Перезагрузка исторических данных
     */
    @PostMapping("/reload-history-volumes")
    public ResponseEntity<String> reloadHistoryVolumes() {
        try {
            log.info("Reloading history volume data...");
            historyVolumeService.reloadHistoryVolumeData();
            return ResponseEntity.ok("History volume data reloaded successfully");
        } catch (Exception e) {
            log.error("Error reloading history volume data", e);
            return ResponseEntity.internalServerError()
                    .body("Error reloading history volume data: " + e.getMessage());
        }
    }

    /**
     * Получение данных о днях из исторических данных
     */
    @GetMapping("/history-days")
    public ResponseEntity<Map<String, Object>> getHistoryDays() {
        try {
            Map<String, Long> totalDays = historyVolumeService.getAllTotalDays();
            Map<String, Long> workingDays = historyVolumeService.getAllWorkingDays();
            Map<String, Long> weekendDays = historyVolumeService.getAllWeekendDays();
            Map<String, Object> stats = historyVolumeService.getHistoryVolumeStats();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("totalDays", totalDays);
            result.put("workingDays", workingDays);
            result.put("weekendDays", weekendDays);
            result.put("stats", stats);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history days data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение данных о днях для конкретного инструмента
     */
    @GetMapping("/history-days/{figi}")
    public ResponseEntity<Map<String, Object>> getHistoryDaysByFigi(@PathVariable String figi) {
        try {
            Map<String, Object> result =
                    Map.of("figi", figi, "totalDays", historyVolumeService.getTotalDays(figi),
                            "workingDays", historyVolumeService.getWorkingDays(figi), "weekendDays",
                            historyVolumeService.getWeekendDays(figi));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history days data for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение средних объемов за день из исторических данных
     */
    @GetMapping("/history-avg-volumes-per-day")
    public ResponseEntity<Map<String, Object>> getHistoryAvgVolumesPerDay() {
        try {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("morningAvgVolumesPerDay",
                    historyVolumeService.getAllMorningAvgVolumesPerDay());
            result.put("mainAvgVolumesPerDay", historyVolumeService.getAllMainAvgVolumesPerDay());
            result.put("eveningAvgVolumesPerDay",
                    historyVolumeService.getAllEveningAvgVolumesPerDay());
            result.put("weekendExchangeAvgVolumesPerDay",
                    historyVolumeService.getAllWeekendExchangeAvgVolumesPerDay());
            result.put("weekendOtcAvgVolumesPerDay",
                    historyVolumeService.getAllWeekendOtcAvgVolumesPerDay());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history avg volumes per day data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение средних объемов за день для конкретного инструмента
     */
    @GetMapping("/history-avg-volumes-per-day/{figi}")
    public ResponseEntity<Map<String, Object>> getHistoryAvgVolumesPerDayByFigi(
            @PathVariable String figi) {
        try {
            Map<String, Object> result = Map.of("figi", figi, "morningAvgVolumePerDay",
                    historyVolumeService.getMorningAvgVolumePerDay(figi), "mainAvgVolumePerDay",
                    historyVolumeService.getMainAvgVolumePerDay(figi), "eveningAvgVolumePerDay",
                    historyVolumeService.getEveningAvgVolumePerDay(figi),
                    "weekendExchangeAvgVolumePerDay",
                    historyVolumeService.getWeekendExchangeAvgVolumePerDay(figi),
                    "weekendOtcAvgVolumePerDay",
                    historyVolumeService.getWeekendOtcAvgVolumePerDay(figi));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history avg volumes per day data for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
