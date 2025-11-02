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
import com.example.investmentdatascannerservice.service.WeekendScannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для сканера выходного дня
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner/weekend-scanner")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class WeekendScannerController {

    private final WeekendScannerService weekendScannerService;
    private final QuoteScannerService quoteScannerService;
    private final PriceCacheService priceCacheService;
    private final QuoteScannerConfig config;

    /**
     * Получить статус сканера выходного дня
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWeekendScannerStatus() {
        Map<String, Object> response = new HashMap<>();

        boolean isActive = quoteScannerService.isScannerActive();
        boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();

        response.put("active", isActive);
        response.put("weekendSession", isWeekendSession);
        response.put("testMode", config.isEnableTestMode());
        response.put("sharesMode", config.isEnableSharesMode());

        String message;
        if (isActive) {
            if (isWeekendSession) {
                message = "Сканер выходного дня активен";
            } else {
                message = "Сканер активен (тестовый режим)";
            }
        } else {
            message = "Сканер выходного дня неактивен";
        }

        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * Запустить сканер выходного дня
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startWeekendScanner() {
        Map<String, Object> response = new HashMap<>();

        try {
            quoteScannerService.startScannerIfWeekendSessionTime();
            boolean isActive = quoteScannerService.isScannerActive();
            boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();

            response.put("success", true);
            response.put("active", isActive);
            response.put("weekendSession", isWeekendSession);

            String message;
            if (isActive) {
                if (isWeekendSession) {
                    message = "Сканер выходного дня запущен";
                } else {
                    message = "Сканер запущен (тестовый режим)";
                }
            } else {
                message = "Сканер не может быть запущен (не время сессии)";
            }

            response.put("message", message);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при запуске сканера выходного дня: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Остановить сканер выходного дня
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopWeekendScanner() {
        Map<String, Object> response = new HashMap<>();

        try {
            quoteScannerService.stopScanner();
            response.put("success", true);
            response.put("active", false);
            response.put("message", "Сканер выходного дня остановлен");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message",
                    "Ошибка при остановке сканера выходного дня: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Проверить, является ли текущее время сессией выходного дня
     */
    @GetMapping("/is-weekend-session")
    public ResponseEntity<Map<String, Object>> isWeekendSessionTime() {
        Map<String, Object> response = new HashMap<>();

        boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();
        response.put("isWeekendSession", isWeekendSession);
        response.put("message", isWeekendSession ? "Сейчас время сессии выходного дня"
                : "Сейчас не время сессии выходного дня");

        return ResponseEntity.ok(response);
    }

    /**
     * Получить текущий список индексов для сканера выходного дня
     */
    @GetMapping("/indices")
    public ResponseEntity<Map<String, Object>> getWeekendScannerIndices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = weekendScannerService.getCurrentIndices();
            log.info("Retrieved {} indices for weekend scanner: {}", indices.size(), indices);

            response.put("success", true);
            response.put("indices", indices);
            response.put("message", "Список индексов сканера выходного дня получен");
        } catch (Exception e) {
            log.error("Error getting indices for weekend scanner", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении списка индексов: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Добавить новый индекс для сканера выходного дня
     */
    @PostMapping("/indices/add")
    public ResponseEntity<Map<String, Object>> addWeekendScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Received add index request for weekend scanner: {}", request);
            String name = request.get("name");
            if (name == null) {
                name = request.get("ticker");
            }
            if (name == null) {
                name = request.get("Ticker");
            }

            String displayName = request.get("displayName");
            if (displayName == null) {
                displayName = request.get("display_name");
            }
            if (displayName == null) {
                displayName = request.get("Display Name");
            }

            if (name == null || name.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Необходимо указать ticker");
                return ResponseEntity.badRequest().body(response);
            }

            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = name;
            }

            log.info("Attempting to add index for weekend scanner: name='{}', displayName='{}'",
                    name, displayName);
            boolean added = weekendScannerService.addIndex(name, displayName);

            if (added) {
                log.info("Successfully added index for weekend scanner: {}", name);
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно добавлен");
            } else {
                log.warn("Failed to add index for weekend scanner (already exists): {}", name);
                response.put("success", false);
                response.put("message", "Индекс " + name + " уже существует");
            }

        } catch (Exception e) {
            log.error("Error adding index for weekend scanner", e);
            response.put("success", false);
            response.put("message", "Ошибка при добавлении индекса: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Удалить индекс для сканера выходного дня
     */
    @DeleteMapping("/indices/remove")
    public ResponseEntity<Map<String, Object>> removeWeekendScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = request.get("name");
            if (name == null) {
                name = request.get("ticker");
            }
            if (name == null) {
                name = request.get("Ticker");
            }

            if (name == null || name.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Необходимо указать name индекса");
                return ResponseEntity.badRequest().body(response);
            }

            boolean removed = weekendScannerService.removeIndex(name);

            if (removed) {
                response.put("success", true);
                response.put("message",
                        "Индекс " + name + " успешно удален из сканера выходного дня");
            } else {
                response.put("success", false);
                response.put("message", "Индекс " + name + " не найден в сканере выходного дня");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при удалении индекса: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Получить цены закрытия для индексов сканера выходного дня
     */
    @GetMapping("/indices/prices")
    public ResponseEntity<Map<String, Object>> getWeekendScannerIndexPrices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = weekendScannerService.getCurrentIndices();
            Map<String, Object> prices = new HashMap<>();

            for (Map<String, String> index : indices) {
                String figi = index.get("figi");
                String name = index.get("name");

                // Получаем цены закрытия из кэша
                BigDecimal closePriceOS = priceCacheService.getLastClosePrice(figi);
                BigDecimal closePriceEvening = priceCacheService.getLastEveningSessionPrice(figi);

                Map<String, Object> indexPrices = new HashMap<>();
                indexPrices.put("figi", figi);
                indexPrices.put("name", name);
                indexPrices.put("displayName", index.get("displayName"));
                indexPrices.put("closePriceOS", closePriceOS);
                indexPrices.put("closePriceEvening", closePriceEvening);

                prices.put(figi, indexPrices);
            }

            response.put("success", true);
            response.put("prices", prices);
            response.put("message", "Цены закрытия для индексов сканера выходного дня получены");
        } catch (Exception e) {
            log.error("Error getting weekend scanner index prices", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении цен закрытия: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}

