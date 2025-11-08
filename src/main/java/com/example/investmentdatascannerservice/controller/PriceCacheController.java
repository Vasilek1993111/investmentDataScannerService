package com.example.investmentdatascannerservice.controller;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
     * Получение информации о планировщике обновлений
     */
    @GetMapping("/scheduler-info")
    public ResponseEntity<Map<String, Object>> getSchedulerInfo() {
        try {
            Map<String, Object> info = new java.util.HashMap<>();
            info.put("schedulerEnabled", true);
            info.put("allPricesUpdateTime", "06:00 MSK (daily with unified weekend logic)");
            info.put("weekendLogic",
                    "Unified logic: weekends use Friday, weekdays use today or last available data");
            info.put("healthCheckInterval", "Every hour");
            info.put("timezone", "Europe/Moscow");
            info.put("lastClosePriceDate", priceCacheService.getLastClosePriceDate());
            info.put("lastEveningSessionDate", priceCacheService.getLastEveningSessionDate());
            info.put("lastOpenPriceDate", priceCacheService.getLastOpenPriceDate());
            info.put("lastPriceDate", priceCacheService.getLastPriceDate());
            info.put("closePricesCount", priceCacheService.getAllClosePrices().size());
            info.put("eveningSessionCount", priceCacheService.getAllEveningSessionPrices().size());
            info.put("openPricesCount", priceCacheService.getAllOpenPrices().size());
            info.put("lastPricesCount", priceCacheService.getAllLastPrices().size());

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Error getting scheduler info", e);
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
     * Получение всех последних цен сделок (last_price) из кэша
     */
    @GetMapping("/last-price")
    public ResponseEntity<Map<String, Object>> getLastPrice() {
        try {
            Map<String, BigDecimal> allLastPrices = priceCacheService.getAllLastPrices();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("prices", allLastPrices);
            result.put("date", priceCacheService.getLastPriceDate());
            result.put("count", allLastPrices.size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting all last prices from cache", e);
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
     * Принудительная перезагрузка всех типов цен с унифицированной логикой выходных дней
     */
    @PostMapping("/force-reload-all")
    public ResponseEntity<Map<String, Object>> forceReloadAllPrices() {
        try {
            log.info("Force reload all prices with unified weekend logic requested");
            priceCacheService.forceReloadAllPricesCache();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message",
                    "All prices cache force reloaded successfully with unified weekend logic");
            result.put("closePricesDate", priceCacheService.getLastClosePriceDate());
            result.put("eveningSessionDate", priceCacheService.getLastEveningSessionDate());
            result.put("openPricesDate", priceCacheService.getLastOpenPriceDate());
            result.put("lastPriceDate", priceCacheService.getLastPriceDate());
            result.put("closePricesCount", priceCacheService.getAllClosePrices().size());
            result.put("eveningSessionCount",
                    priceCacheService.getAllEveningSessionPrices().size());
            result.put("openPricesCount", priceCacheService.getAllOpenPrices().size());
            result.put("lastPricesCount", priceCacheService.getAllLastPrices().size());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error force reloading all prices", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message",
                    "Error force reloading all prices: " + e.getMessage()));
        }
    }

    /**
     * Ручное обновление только цен закрытия
     */
    @PostMapping("/reload-close-prices")
    public ResponseEntity<Map<String, Object>> reloadClosePrices() {
        try {
            log.info("Manual reload close prices requested");
            priceCacheService.forceReloadClosePricesCache();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "Close prices cache reloaded successfully");
            result.put("lastUpdateDate", priceCacheService.getLastClosePriceDate());
            result.put("pricesCount", priceCacheService.getAllClosePrices().size());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error reloading close prices", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message",
                    "Error reloading close prices: " + e.getMessage()));
        }
    }

    /**
     * Ручное обновление только последних цен сделок (last_price)
     */
    @PostMapping("/reload-last-prices")
    public ResponseEntity<Map<String, Object>> reloadLastPrices() {
        try {
            log.info("Manual reload last prices requested");
            priceCacheService.forceReloadLastPricesCache();

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", true);
            result.put("message", "Last prices cache reloaded successfully");
            result.put("lastUpdateDate", priceCacheService.getLastPriceDate());
            result.put("pricesCount", priceCacheService.getAllLastPrices().size());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error reloading last prices", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message",
                    "Error reloading last prices: " + e.getMessage()));
        }
    }

    /**
     * Проверка наличия данных для конкретного figi в базе данных last_prices
     */
    @GetMapping("/check-last-price/{figi}")
    public ResponseEntity<Map<String, Object>> checkLastPriceInDatabase(@PathVariable String figi) {
        try {
            log.info("Checking last price in database for figi: {}", figi);
            Map<String, Object> result = priceCacheService.checkLastPriceInDatabase(figi);

            // Добавляем информацию о кэше
            BigDecimal cachedPrice = priceCacheService.getLastPrice(figi);
            result.put("inCache", cachedPrice != null);
            result.put("cachedPrice", cachedPrice);
            result.put("cacheDate", priceCacheService.getLastPriceDate());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error checking last price in database for figi: {}", figi, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Error checking last price: " + e.getMessage()));
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
     * Получение последней цены закрытия для конкретного инструмента
     */
    @GetMapping("/last-close-price")
    public ResponseEntity<Map<String, Object>> getLastClosePrice(@RequestParam String figi) {
        try {
            BigDecimal closePrice = priceCacheService.getLastClosePrice(figi);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("figi", figi);
            result.put("closePrice", closePrice);
            result.put("date", priceCacheService.getLastClosePriceDate());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting last close price for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение всех цен по FIGI или тикеру из кэша
     * 
     * Метод автоматически определяет, является ли переданный параметр FIGI или тикером, и
     * возвращает цены для правильного инструмента
     */
    @GetMapping("/prices/{figi}")
    public ResponseEntity<Map<String, Object>> getPricesByFigi(@PathVariable String figi) {
        try {
            // Сначала проверяем, есть ли цены для переданного параметра как FIGI
            Map<String, BigDecimal> prices = priceCacheService.getPricesForFigi(figi);

            // Если цены есть, значит это был FIGI
            if (hasPrices(prices)) {
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("figi", figi);

                // Добавляем объект prices с конкретными ценами
                Map<String, Object> pricesObject = new java.util.HashMap<>();
                pricesObject.put("openPrice", prices.get("openPrice"));
                pricesObject.put("closePrice", prices.get("closePrice"));
                pricesObject.put("eveningSessionPrice", prices.get("eveningSessionPrice"));
                pricesObject.put("lastPrice", prices.get("lastPrice"));
                result.put("prices", pricesObject);

                result.put("dates",
                        Map.of("closePriceDate", priceCacheService.getLastClosePriceDate(),
                                "eveningSessionPriceDate",
                                priceCacheService.getLastEveningSessionPriceDate(), "openPriceDate",
                                priceCacheService.getLastOpenPriceDate(), "lastPriceDate",
                                priceCacheService.getLastPriceDate()));
                return ResponseEntity.ok(result);
            }

            // Если цен нет, возможно переданный параметр - это тикер
            // Пытаемся найти FIGI по тикеру
            String actualFigi = instrumentCacheService.getFigiByTicker(figi);

            if (actualFigi != null && !actualFigi.equals(figi)) {
                // Нашли FIGI по тикеру, получаем цены
                prices = priceCacheService.getPricesForFigi(actualFigi);

                if (hasPrices(prices)) {
                    log.debug("Found FIGI {} for ticker {}, returning prices", actualFigi, figi);
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("figi", actualFigi);
                    result.put("ticker", figi);

                    // Добавляем объект prices с конкретными ценами
                    Map<String, Object> pricesObject = new java.util.HashMap<>();
                    pricesObject.put("openPrice", prices.get("openPrice"));
                    pricesObject.put("closePrice", prices.get("closePrice"));
                    pricesObject.put("eveningSessionPrice", prices.get("eveningSessionPrice"));
                    pricesObject.put("lastPrice", prices.get("lastPrice"));
                    result.put("prices", pricesObject);

                    result.put("dates",
                            Map.of("closePriceDate", priceCacheService.getLastClosePriceDate(),
                                    "eveningSessionPriceDate",
                                    priceCacheService.getLastEveningSessionPriceDate(),
                                    "openPriceDate", priceCacheService.getLastOpenPriceDate(),
                                    "lastPriceDate", priceCacheService.getLastPriceDate()));
                    return ResponseEntity.ok(result);
                }
            }

            // Если цены не найдены, возвращаем пустой результат с исходным параметром
            log.warn("No prices found for parameter: {} (treated as {}: {})", figi,
                    actualFigi != null ? "ticker, mapped to FIGI" : "FIGI", actualFigi);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("figi", actualFigi != null ? actualFigi : figi);
            if (actualFigi != null) {
                result.put("ticker", figi);
            }

            Map<String, Object> pricesObject = new java.util.HashMap<>();
            pricesObject.put("openPrice", null);
            pricesObject.put("closePrice", null);
            pricesObject.put("eveningSessionPrice", null);
            pricesObject.put("lastPrice", null);
            result.put("prices", pricesObject);

            result.put("dates", Map.of("closePriceDate", priceCacheService.getLastClosePriceDate(),
                    "eveningSessionPriceDate", priceCacheService.getLastEveningSessionPriceDate(),
                    "openPriceDate", priceCacheService.getLastOpenPriceDate(), "lastPriceDate",
                    priceCacheService.getLastPriceDate()));

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting prices for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Проверить, есть ли цены в результате
     */
    private boolean hasPrices(Map<String, BigDecimal> prices) {
        return prices != null && ((prices.get("openPrice") != null
                && prices.get("openPrice").compareTo(BigDecimal.ZERO) > 0)
                || (prices.get("closePrice") != null
                        && prices.get("closePrice").compareTo(BigDecimal.ZERO) > 0)
                || (prices.get("eveningSessionPrice") != null
                        && prices.get("eveningSessionPrice").compareTo(BigDecimal.ZERO) > 0)
                || (prices.get("lastPrice") != null
                        && prices.get("lastPrice").compareTo(BigDecimal.ZERO) > 0));
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
     * Получение всех данных объемов (исторических и сегодняшних)
     */
    @GetMapping("/volumes")
    public ResponseEntity<Map<String, Object>> getVolumes() {
        try {
            Map<String, Long> totalVolumes = historyVolumeService.getAllTotalVolumes();
            Map<String, Long> morningVolumes = historyVolumeService.getAllMorningSessionVolumes();
            Map<String, Long> mainVolumes = historyVolumeService.getAllMainSessionVolumes();
            Map<String, Long> eveningVolumes = historyVolumeService.getAllEveningSessionVolumes();
            Map<String, Long> weekendExchangeVolumes =
                    historyVolumeService.getAllWeekendExchangeVolumes();
            Map<String, Long> weekendOtcVolumes = historyVolumeService.getAllWeekendOtcVolumes();
            Map<String, Object> stats = historyVolumeService.getHistoryVolumeStats();

            // Добавляем средние объемы за день
            Map<String, Object> avgVolumesPerDay = new java.util.HashMap<>();
            avgVolumesPerDay.put("morningAvgVolumesPerDay",
                    historyVolumeService.getAllMorningAvgVolumesPerDay());
            avgVolumesPerDay.put("mainAvgVolumesPerDay",
                    historyVolumeService.getAllMainAvgVolumesPerDay());
            avgVolumesPerDay.put("eveningAvgVolumesPerDay",
                    historyVolumeService.getAllEveningAvgVolumesPerDay());
            avgVolumesPerDay.put("weekendExchangeAvgVolumesPerDay",
                    historyVolumeService.getAllWeekendExchangeAvgVolumesPerDay());
            avgVolumesPerDay.put("weekendOtcAvgVolumesPerDay",
                    historyVolumeService.getAllWeekendOtcAvgVolumesPerDay());

            // Сегодняшние данные
            Map<String, Long> todayVolumes = todayVolumeService.getAllTotalVolumes();
            Map<String, Object> todayStats = todayVolumeService.getTodayVolumeStats();

            Map<String, Object> result = new java.util.HashMap<>();

            // Исторические данные
            result.put("totalVolumes", totalVolumes);
            result.put("morningVolumes", morningVolumes);
            result.put("mainVolumes", mainVolumes);
            result.put("eveningVolumes", eveningVolumes);
            result.put("weekendExchangeVolumes", weekendExchangeVolumes);
            result.put("weekendOtcVolumes", weekendOtcVolumes);
            result.put("avgVolumesPerDay", avgVolumesPerDay);
            result.put("historyStats", stats);

            // Сегодняшние данные
            result.put("todayVolumes", todayVolumes);
            result.put("todayStats", todayStats);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history volume data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение всех данных объемов для конкретного инструмента
     */
    @GetMapping("/volumes/{figi}")
    public ResponseEntity<Map<String, Object>> getVolumesByFigi(@PathVariable String figi) {
        try {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("figi", figi);

            // Группируем данные по объектам
            Map<String, Object> volumes = new java.util.HashMap<>();
            volumes.put("totalVolume", historyVolumeService.getTotalVolume(figi));
            volumes.put("morningSessionVolume", historyVolumeService.getMorningSessionVolume(figi));
            volumes.put("mainSessionVolume", historyVolumeService.getMainSessionVolume(figi));
            volumes.put("eveningSessionVolume", historyVolumeService.getEveningSessionVolume(figi));
            volumes.put("weekendExchangeVolume",
                    historyVolumeService.getWeekendExchangeVolume(figi));
            volumes.put("weekendOtcVolume", historyVolumeService.getWeekendOtcVolume(figi));
            volumes.put("todayVolume", todayVolumeService.getTotalVolume(figi));
            result.put("volumes", volumes);

            Map<String, Object> avg = new java.util.HashMap<>();
            avg.put("avgVolumePerCandle", historyVolumeService.getAvgVolumePerCandle(figi));
            avg.put("todayAvgVolumePerCandle", todayVolumeService.getAvgVolumePerCandle(figi));
            result.put("avg", avg);

            Map<String, Object> candles = new java.util.HashMap<>();
            candles.put("totalCandles", historyVolumeService.getTotalCandles(figi));
            candles.put("todayCandles", todayVolumeService.getTotalCandles(figi));
            result.put("candles", candles);

            Map<String, Object> volumePerDays = new java.util.HashMap<>();
            volumePerDays.put("morningAvgVolumePerDay",
                    historyVolumeService.getMorningAvgVolumePerDay(figi));
            volumePerDays.put("mainAvgVolumePerDay",
                    historyVolumeService.getMainAvgVolumePerDay(figi));
            volumePerDays.put("eveningAvgVolumePerDay",
                    historyVolumeService.getEveningAvgVolumePerDay(figi));
            volumePerDays.put("weekendExchangeAvgVolumePerDay",
                    historyVolumeService.getWeekendExchangeAvgVolumePerDay(figi));
            volumePerDays.put("weekendOtcAvgVolumePerDay",
                    historyVolumeService.getWeekendOtcAvgVolumePerDay(figi));
            result.put("volumePerDays", volumePerDays);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting history volume data for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Перезагрузка данных объемов
     */
    @PostMapping("/reload-volumes")
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

}
