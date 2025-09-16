package com.example.investmentdatascannerservice.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.service.PriceCacheService;
import com.example.investmentdatascannerservice.service.StartupPriceLoader;
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
     * Получение цены закрытия для конкретного инструмента и даты
     */
    @GetMapping("/close-price")
    public ResponseEntity<BigDecimal> getClosePrice(@RequestParam String figi,
            @RequestParam LocalDate date) {
        try {
            BigDecimal price = priceCacheService.getClosePrice(figi, date);
            if (price != null) {
                return ResponseEntity.ok(price);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting close price for figi: {}, date: {}", figi, date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение цены вечерней сессии для конкретного инструмента и даты
     */
    @GetMapping("/evening-session-price")
    public ResponseEntity<BigDecimal> getEveningSessionPrice(@RequestParam String figi,
            @RequestParam LocalDate date) {
        try {
            BigDecimal price = priceCacheService.getEveningSessionPrice(figi, date);
            if (price != null) {
                return ResponseEntity.ok(price);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting evening session price for figi: {}, date: {}", figi, date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение последней цены закрытия для инструмента
     */
    @GetMapping("/last-close-price")
    public ResponseEntity<BigDecimal> getLastClosePrice(@RequestParam String figi) {
        try {
            BigDecimal price = priceCacheService.getLastClosePrice(figi);
            if (price != null) {
                return ResponseEntity.ok(price);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting last close price for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение последней цены вечерней сессии для инструмента
     */
    @GetMapping("/last-evening-session-price")
    public ResponseEntity<BigDecimal> getLastEveningSessionPrice(@RequestParam String figi) {
        try {
            BigDecimal price = priceCacheService.getLastEveningSessionPrice(figi);
            if (price != null) {
                return ResponseEntity.ok(price);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting last evening session price for figi: {}", figi, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение цен закрытия для списка инструментов за определенную дату
     */
    @GetMapping("/close-prices")
    public ResponseEntity<Map<String, BigDecimal>> getClosePricesForDate(
            @RequestParam List<String> figis, @RequestParam LocalDate date) {
        try {
            Map<String, BigDecimal> prices = priceCacheService.getClosePricesForDate(figis, date);
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            log.error("Error getting close prices for figis: {}, date: {}", figis, date, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение цен вечерней сессии для списка инструментов за определенную дату
     */
    @GetMapping("/evening-session-prices")
    public ResponseEntity<Map<String, BigDecimal>> getEveningSessionPricesForDate(
            @RequestParam List<String> figis, @RequestParam LocalDate date) {
        try {
            Map<String, BigDecimal> prices =
                    priceCacheService.getEveningSessionPricesForDate(figis, date);
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            log.error("Error getting evening session prices for figis: {}, date: {}", figis, date,
                    e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение последних цен закрытия для списка инструментов
     */
    @GetMapping("/last-close-prices")
    public ResponseEntity<Map<String, BigDecimal>> getLastClosePrices(
            @RequestParam List<String> figis) {
        try {
            Map<String, BigDecimal> prices = priceCacheService.getLastClosePrices(figis);
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            log.error("Error getting last close prices for figis: {}", figis, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение последних цен вечерней сессии для списка инструментов
     */
    @GetMapping("/last-evening-session-prices")
    public ResponseEntity<Map<String, BigDecimal>> getLastEveningSessionPrices(
            @RequestParam List<String> figis) {
        try {
            Map<String, BigDecimal> prices = priceCacheService.getLastEveningSessionPrices(figis);
            return ResponseEntity.ok(prices);
        } catch (Exception e) {
            log.error("Error getting last evening session prices for figis: {}", figis, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Перезагрузка кэша
     */
    @PostMapping("/reload")
    public ResponseEntity<String> reloadCache() {
        try {
            log.info("Manual cache reload requested");
            priceCacheService.reloadCache();
            return ResponseEntity.ok("Cache reloaded successfully");
        } catch (Exception e) {
            log.error("Error reloading cache", e);
            return ResponseEntity.internalServerError()
                    .body("Error reloading cache: " + e.getMessage());
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
     * Принудительная перезагрузка всех цен
     */
    @PostMapping("/reload-all")
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
     * Обновление цен из API
     */
    @PostMapping("/refresh-from-api")
    public ResponseEntity<String> refreshPricesFromApi() {
        try {
            log.info("Manual refresh from API requested");
            startupPriceLoader.refreshPricesFromApi();
            return ResponseEntity.ok("Price refresh from API started");
        } catch (Exception e) {
            log.error("Error refreshing prices from API", e);
            return ResponseEntity.internalServerError()
                    .body("Error refreshing prices from API: " + e.getMessage());
        }
    }
}
