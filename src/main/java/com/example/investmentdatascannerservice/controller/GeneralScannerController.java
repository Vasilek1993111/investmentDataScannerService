package com.example.investmentdatascannerservice.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.entity.ShareEntity;
import com.example.investmentdatascannerservice.service.QuoteScannerService;
import com.example.investmentdatascannerservice.utils.FutureService;
import com.example.investmentdatascannerservice.utils.SessionTimeService;
import com.example.investmentdatascannerservice.utils.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Общий контроллер для базовой функциональности сканеров
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class GeneralScannerController {

    private final QuoteScannerService quoteScannerService;
    private final ShareService shareService;
    private final FutureService futureService;
    private final QuoteScannerConfig config;
    private final SessionTimeService sessionTimeService;

    /**
     * Получить общую статистику сканера
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = quoteScannerService.getStats();
        stats.put("sharesMode", config.isEnableSharesMode());
        stats.put("totalShares", shareService.getAllSharesCount());
        return ResponseEntity.ok(stats);
    }

    /**
     * Получить текущие цены всех инструментов
     */
    @GetMapping("/current-prices")
    public ResponseEntity<Map<String, Object>> getCurrentPrices() {
        return ResponseEntity.ok(quoteScannerService.getCurrentPrices());
    }

    /**
     * Получить список отслеживаемых инструментов
     */
    @GetMapping("/instruments")
    public ResponseEntity<Map<String, Object>> getInstruments() {
        return ResponseEntity
                .ok(Map.of("instruments",
                        quoteScannerService.getInstrumentCacheService().getInstrumentsForScanning(),
                        "count", quoteScannerService.getInstrumentCacheService()
                                .getInstrumentsForScanning().size(),
                        "sharesMode", config.isEnableSharesMode()));
    }

    /**
     * Получить все доступные инструменты с ценами
     */
    @GetMapping("/available-instruments")
    public ResponseEntity<Map<String, Object>> getAvailableInstruments() {
        return ResponseEntity.ok(Map.of("instruments",
                quoteScannerService.getInstrumentCacheService().getInstrumentsForScanning(),
                "names",
                quoteScannerService.getInstrumentCacheService().getInstrumentNamesForScanning(),
                "count",
                quoteScannerService.getInstrumentCacheService().getInstrumentsForScanning().size(),
                "sharesMode", config.isEnableSharesMode()));
    }

    /**
     * Получить статус тестового режима
     */
    @GetMapping("/test-mode")
    public ResponseEntity<Map<String, Object>> getTestModeStatus() {
        return ResponseEntity.ok(Map.of("testModeEnabled", config.isEnableTestMode(),
                "scannerActive", quoteScannerService.isScannerActive(), "sharesMode",
                config.isEnableSharesMode()));
    }

    /**
     * Найти акции по тикеру
     */
    @GetMapping("/shares/search/{ticker}")
    public ResponseEntity<Map<String, Object>> searchSharesByTicker(@PathVariable String ticker) {
        try {
            List<ShareEntity> shares = shareService.findByTicker(ticker);
            return ResponseEntity.ok(Map.of("ticker", ticker, "shares", shares, "count",
                    shares.size(), "found", !shares.isEmpty()));
        } catch (Exception e) {
            log.error("Error searching shares by ticker: {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получить все фьючерсы с данными об экспирации
     */
    @GetMapping("/futures")
    public ResponseEntity<Map<String, Object>> getAllFutures() {
        try {
            var futures = futureService.getAllFutures();
            return ResponseEntity.ok(Map.of("futures", futures, "count", futures.size()));
        } catch (Exception e) {
            log.error("Error getting futures data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Перезагрузить кэш инструментов
     */
    @GetMapping("/reload-cache")
    public ResponseEntity<Map<String, Object>> reloadCache() {
        try {
            quoteScannerService.getInstrumentCacheService().reloadCache();
            return ResponseEntity
                    .ok(Map.of("success", true, "message", "Cache reloaded successfully"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message",
                    "Error reloading cache: " + e.getMessage()));
        }
    }

    /**
     * Получить статус сканера
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();

        boolean isActive = quoteScannerService.isScannerActive();
        boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();
        boolean isMorningSession = sessionTimeService.isMorningSessionTime();

        response.put("active", isActive);
        response.put("weekendSession", isWeekendSession);
        response.put("morningSession", isMorningSession);
        response.put("testMode", config.isEnableTestMode());
        response.put("sharesMode", config.isEnableSharesMode());

        String message;
        if (isActive) {
            if (isWeekendSession) {
                message = "Сканер выходного дня активен";
            } else if (isMorningSession) {
                message = "Утренний сканер активен";
            } else {
                message = "Сканер активен (тестовый режим)";
            }
        } else {
            message = "Сканер неактивен";
        }

        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * Запустить сканер
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startScanner() {
        Map<String, Object> response = new HashMap<>();

        try {
            quoteScannerService.startScannerIfWeekendSessionTime();
            boolean isActive = quoteScannerService.isScannerActive();
            boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();
            boolean isMorningSession = sessionTimeService.isMorningSessionTime();

            response.put("success", true);
            response.put("active", isActive);
            response.put("weekendSession", isWeekendSession);
            response.put("morningSession", isMorningSession);

            String message;
            if (isActive) {
                if (isWeekendSession) {
                    message = "Сканер выходного дня запущен";
                } else if (isMorningSession) {
                    message = "Утренний сканер запущен";
                } else {
                    message = "Сканер запущен (тестовый режим)";
                }
            } else {
                message = "Сканер не может быть запущен (не время сессии)";
            }

            response.put("message", message);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при запуске сканера: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Остановить сканер
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopScanner() {
        Map<String, Object> response = new HashMap<>();

        try {
            quoteScannerService.stopScanner();
            response.put("success", true);
            response.put("active", false);
            response.put("message", "Сканер остановлен");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при остановке сканера: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Проверить, является ли текущее время сессией выходного дня
     */
    @GetMapping("/is-weekend-session")
    public ResponseEntity<Map<String, Object>> isWeekendSession() {
        Map<String, Object> response = new HashMap<>();

        boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();
        response.put("isWeekendSession", isWeekendSession);
        response.put("message", isWeekendSession ? "Сейчас время сессии выходного дня"
                : "Сейчас не время сессии выходного дня");

        return ResponseEntity.ok(response);
    }

    /**
     * Проверить, является ли текущее время утренней сессией
     */
    @GetMapping("/is-morning-session")
    public ResponseEntity<Map<String, Object>> isMorningSession() {
        Map<String, Object> response = new HashMap<>();

        boolean isMorningSession = sessionTimeService.isMorningSessionTime();
        response.put("isMorningSession", isMorningSession);
        response.put("message", isMorningSession ? "Сейчас время утренней сессии"
                : "Сейчас не время утренней сессии");

        return ResponseEntity.ok(response);
    }

    /**
     * Тестовый endpoint для проверки работы API
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "API работает");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    /**
     * Получить текущий список индексов (общие индексы)
     */
    @GetMapping("/indices")
    public ResponseEntity<Map<String, Object>> getIndices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = quoteScannerService.getCurrentIndices();
            log.info("Retrieved {} indices: {}", indices.size(), indices);

            response.put("success", true);
            response.put("indices", indices);
            response.put("message", "Список индексов получен");
        } catch (Exception e) {
            log.error("Error getting indices", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении списка индексов: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Добавить новый индекс (общие индексы)
     */
    @PostMapping("/indices/add")
    public ResponseEntity<Map<String, Object>> addIndex(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Received add index request: {}", request);
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

            log.info("Attempting to add index: name='{}', displayName='{}'", name, displayName);
            boolean added = quoteScannerService.addIndex(name, displayName);

            if (added) {
                log.info("Successfully added index: {}", name);
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно добавлен");
            } else {
                log.warn("Failed to add index (already exists): {}", name);
                response.put("success", false);
                response.put("message", "Индекс " + name + " уже существует");
            }

        } catch (Exception e) {
            log.error("Error adding index", e);
            response.put("success", false);
            response.put("message", "Ошибка при добавлении индекса: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Удалить индекс по name (общие индексы)
     */
    @DeleteMapping("/indices/remove")
    public ResponseEntity<Map<String, Object>> removeIndex(
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

            boolean removed = quoteScannerService.removeIndex(name);

            if (removed) {
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно удален");
            } else {
                response.put("success", false);
                response.put("message", "Индекс " + name + " не найден");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при удалении индекса: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}

