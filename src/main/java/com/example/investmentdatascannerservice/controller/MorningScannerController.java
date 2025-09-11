package com.example.investmentdatascannerservice.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.config.QuoteScannerConfig;
import com.example.investmentdatascannerservice.service.QuoteScannerService;
import com.example.investmentdatascannerservice.service.ShareService;

/**
 * REST контроллер для утреннего сканера Использует все акции из таблицы invest.shares
 */
@RestController
@RequestMapping("/api/morning-scanner")
public class MorningScannerController {

    private final QuoteScannerService quoteScannerService;
    private final ShareService shareService;
    private final QuoteScannerConfig config;

    public MorningScannerController(QuoteScannerService quoteScannerService,
            ShareService shareService, QuoteScannerConfig config) {
        this.quoteScannerService = quoteScannerService;
        this.shareService = shareService;
        this.config = config;
    }

    /**
     * Получить статистику утреннего сканера
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
     * Получить список отслеживаемых инструментов (акции)
     */
    @GetMapping("/instruments")
    public ResponseEntity<Map<String, Object>> getInstruments() {
        return ResponseEntity
                .ok(Map.of("instruments", quoteScannerService.getInstrumentsForScanning(), "count",
                        quoteScannerService.getInstrumentsForScanning().size(), "sharesMode",
                        config.isEnableSharesMode()));
    }

    /**
     * Получить все доступные акции с ценами
     */
    @GetMapping("/available-shares")
    public ResponseEntity<Map<String, Object>> getAvailableShares() {
        return ResponseEntity
                .ok(Map.of("instruments", quoteScannerService.getInstrumentsForScanning(), "names",
                        quoteScannerService.getInstrumentNamesForScanning(), "count",
                        quoteScannerService.getInstrumentsForScanning().size(), "sharesMode",
                        config.isEnableSharesMode()));
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
}
