package com.example.investmentdatascannerservice.controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.service.PriceCacheService;
import com.example.investmentdatascannerservice.service.QuoteScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для сканера фьючерсов
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner/futures-scanner")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FuturesScannerController {

    private final QuoteScannerService quoteScannerService;
    private final PriceCacheService priceCacheService;
    private final QuoteScannerConfig config;

    /**
     * Получить текущий список индексов для сканера фьючерсов
     */
    @GetMapping("/indices")
    public ResponseEntity<Map<String, Object>> getFuturesScannerIndices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = quoteScannerService.getCurrentIndices();
            log.info("Retrieved {} indices for futures scanner: {}", indices.size(), indices);

            response.put("success", true);
            response.put("indices", indices);
            response.put("message", "Список индексов сканера фьючерсов получен");
        } catch (Exception e) {
            log.error("Error getting indices for futures scanner", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении списка индексов: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Добавить новый индекс для сканера фьючерсов
     */
    @PostMapping("/indices/add")
    public ResponseEntity<Map<String, Object>> addFuturesScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = request.get("name");
            if (name == null) {
                name = request.get("ticker");
            }
            String displayName = request.get("displayName");
            if (displayName == null) {
                displayName = request.get("display_name");
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = name;
            }

            if (name == null || name.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Необходимо указать name индекса");
                return ResponseEntity.badRequest().body(response);
            }

            boolean added = quoteScannerService.addIndex(name, displayName);

            if (added) {
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно добавлен");
            } else {
                response.put("success", false);
                response.put("message", "Индекс " + name + " уже существует");
            }

        } catch (Exception e) {
            log.error("Error adding index for futures scanner", e);
            response.put("success", false);
            response.put("message", "Ошибка при добавлении индекса: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Удалить индекс для сканера фьючерсов
     */
    @DeleteMapping("/indices/remove")
    public ResponseEntity<Map<String, Object>> removeFuturesScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = request.get("name");
            if (name == null) {
                name = request.get("ticker");
            }

            if (name == null || name.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Необходимо указать name индекса");
                return ResponseEntity.badRequest().body(response);
            }

            boolean removed = quoteScannerService.removeIndex(name);

            if (removed) {
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно удален");
            } else {
                response.put("success", false);
                response.put("message", "Индекс " + name + " не найден");
            }

        } catch (Exception e) {
            log.error("Error removing index for futures scanner", e);
            response.put("success", false);
            response.put("message", "Ошибка при удалении индекса: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Получить цены закрытия для индексов сканера фьючерсов
     */
    @GetMapping("/indices/prices")
    public ResponseEntity<Map<String, Object>> getFuturesScannerIndexPrices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = quoteScannerService.getCurrentIndices();
            Map<String, Object> prices = new HashMap<>();

            for (Map<String, String> index : indices) {
                String figi = index.get("figi");
                String name = index.get("name");

                // Получаем цены из кэша
                BigDecimal closePriceOS = priceCacheService.getLastClosePrice(figi);
                BigDecimal closePriceEvening = priceCacheService.getLastEveningSessionPrice(figi);
                BigDecimal lastPrice = priceCacheService.getLastPrice(figi);

                Map<String, Object> indexPrices = new HashMap<>();
                indexPrices.put("figi", figi);
                indexPrices.put("name", name);
                indexPrices.put("displayName", index.get("displayName"));
                indexPrices.put("closePriceOS", closePriceOS);
                indexPrices.put("closePriceEvening", closePriceEvening);
                indexPrices.put("lastPrice", lastPrice);

                prices.put(figi, indexPrices);
            }

            response.put("success", true);
            response.put("prices", prices);
            response.put("message", "Цены закрытия для индексов сканера фьючерсов получены");
        } catch (Exception e) {
            log.error("Error getting futures scanner index prices", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении цен закрытия: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Проверить активность сканера фьючерсов
     */
    @GetMapping("/is-active")
    public ResponseEntity<Map<String, Object>> isFuturesScannerActive() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isActive = quoteScannerService.isScannerActive();
            response.put("isActive", isActive);
            response.put("testMode", config.isTestModeFutures());
            response.put("message",
                    isActive ? "Сканер фьючерсов активен" : "Сканер фьючерсов неактивен");
        } catch (Exception e) {
            log.error("Error checking futures scanner status", e);
            response.put("isActive", false);
            response.put("testMode", config.isTestModeFutures());
            response.put("message", "Ошибка при проверке статуса сканера: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Получить статус сканера фьючерсов
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getFuturesScannerStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            boolean isActive = quoteScannerService.isScannerActive();
            response.put("active", isActive);
            response.put("testMode", config.isTestModeFutures());
            response.put("message",
                    isActive ? "Сканер фьючерсов активен" : "Сканер фьючерсов неактивен");
        } catch (Exception e) {
            log.error("Error getting futures scanner status", e);
            response.put("active", false);
            response.put("testMode", config.isTestModeFutures());
            response.put("message", "Ошибка при получении статуса сканера: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}

