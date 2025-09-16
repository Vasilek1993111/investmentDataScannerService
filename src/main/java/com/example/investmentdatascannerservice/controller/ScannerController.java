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
import com.example.investmentdatascannerservice.service.MorningScannerService;
import com.example.investmentdatascannerservice.service.PriceCacheService;
import com.example.investmentdatascannerservice.service.QuoteScannerService;
import com.example.investmentdatascannerservice.service.WeekendScannerService;
import com.example.investmentdatascannerservice.utils.SessionTimeService;
import com.example.investmentdatascannerservice.utils.ShareService;
import lombok.extern.slf4j.Slf4j;

/**
 * Объединенный REST контроллер для всех типов сканеров
 *
 * Объединяет функциональность утреннего сканера, сканера котировок и сканера выходного дня
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner")
@CrossOrigin(origins = "*")
public class ScannerController {

    private final QuoteScannerService quoteScannerService;
    private final ShareService shareService;
    private final QuoteScannerConfig config;
    private final SessionTimeService sessionTimeService;
    private final MorningScannerService morningScannerService;
    private final WeekendScannerService weekendScannerService;
    private final PriceCacheService priceCacheService;

    public ScannerController(QuoteScannerService quoteScannerService, ShareService shareService,
            QuoteScannerConfig config, SessionTimeService sessionTimeService,
            MorningScannerService morningScannerService,
            WeekendScannerService weekendScannerService, PriceCacheService priceCacheService) {
        this.quoteScannerService = quoteScannerService;
        this.shareService = shareService;
        this.config = config;
        this.sessionTimeService = sessionTimeService;
        this.morningScannerService = morningScannerService;
        this.weekendScannerService = weekendScannerService;
        this.priceCacheService = priceCacheService;
    }

    // ==================== ОБЩИЕ ENDPOINTS ====================

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

    // ==================== УПРАВЛЕНИЕ СКАНЕРОМ ====================

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

    // ==================== СЕССИИ ====================

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

    // ==================== УПРАВЛЕНИЕ ИНДЕКСАМИ ====================

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

    // ==================== MORNING SCANNER ENDPOINTS ====================

    /**
     * Получить текущий список индексов для утреннего сканера
     */
    @GetMapping("/morning-scanner/indices")
    public ResponseEntity<Map<String, Object>> getMorningScannerIndices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = morningScannerService.getCurrentIndices();
            log.info("Retrieved {} indices for morning scanner: {}", indices.size(), indices);

            response.put("success", true);
            response.put("indices", indices);
            response.put("message", "Список индексов утреннего сканера получен");
        } catch (Exception e) {
            log.error("Error getting indices for morning scanner", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении списка индексов: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Добавить новый индекс для утреннего сканера
     */
    @PostMapping("/morning-scanner/indices/add")
    public ResponseEntity<Map<String, Object>> addMorningScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Поддерживаем разные варианты полей от фронтенда
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

            log.info("Attempting to add morning scanner index: name='{}', displayName='{}'", name,
                    displayName);
            boolean added = morningScannerService.addIndex(name, displayName);

            if (added) {
                log.info("Successfully added morning scanner index: {}", name);
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно добавлен в утренний сканер");
            } else {
                response.put("success", false);
                response.put("message", "Индекс " + name + " уже существует в утреннем сканере");
            }

        } catch (Exception e) {
            log.error("Error adding morning scanner index", e);
            response.put("success", false);
            response.put("message", "Ошибка при добавлении индекса: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Удалить индекс для утреннего сканера
     */
    @DeleteMapping("/morning-scanner/indices/remove")
    public ResponseEntity<Map<String, Object>> removeMorningScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Поддерживаем разные варианты полей от фронтенда
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

            boolean removed = morningScannerService.removeIndex(name);

            if (removed) {
                response.put("success", true);
                response.put("message", "Индекс " + name + " успешно удален из утреннего сканера");
            } else {
                response.put("success", false);
                response.put("message", "Индекс " + name + " не найден в утреннем сканере");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при удалении индекса: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Получить цены закрытия для индексов утреннего сканера
     */
    @GetMapping("/morning-scanner/indices/prices")
    public ResponseEntity<Map<String, Object>> getMorningScannerIndexPrices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = morningScannerService.getCurrentIndices();
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
            response.put("message", "Цены закрытия для индексов утреннего сканера получены");
        } catch (Exception e) {
            log.error("Error getting morning scanner index prices", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении цен закрытия: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // ==================== WEEKEND SCANNER ENDPOINTS ====================

    /**
     * Получить статус сканера выходного дня
     */
    @GetMapping("/weekend-scanner/status")
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
    @PostMapping("/weekend-scanner/start")
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
    @PostMapping("/weekend-scanner/stop")
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
    @GetMapping("/weekend-scanner/is-weekend-session")
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
    @GetMapping("/weekend-scanner/indices")
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
    @PostMapping("/weekend-scanner/indices/add")
    public ResponseEntity<Map<String, Object>> addWeekendScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Логируем входящий запрос для отладки
            log.info("Received add index request for weekend scanner: {}", request);
            // Поддерживаем разные варианты полей от фронтенда
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
    @DeleteMapping("/weekend-scanner/indices/remove")
    public ResponseEntity<Map<String, Object>> removeWeekendScannerIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Поддерживаем разные варианты полей от фронтенда
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
    @GetMapping("/weekend-scanner/indices/prices")
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

    /**
     * Получить текущий список индексов
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
     * Добавить новый индекс
     */
    @PostMapping("/indices/add")
    public ResponseEntity<Map<String, Object>> addIndex(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Логируем входящий запрос для отладки
            log.info("Received add index request: {}", request);
            // Поддерживаем разные варианты полей от фронтенда
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
     * Удалить индекс по name
     */
    @DeleteMapping("/indices/remove")
    public ResponseEntity<Map<String, Object>> removeIndex(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Поддерживаем разные варианты полей от фронтенда
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
