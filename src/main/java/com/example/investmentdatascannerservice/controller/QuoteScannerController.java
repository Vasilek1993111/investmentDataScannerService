package com.example.investmentdatascannerservice.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.service.QuoteScannerService;

/**
 * REST контроллер для управления сканером котировок
 */
@RestController
@RequestMapping("/api/quote-scanner")
public class QuoteScannerController {

    private final QuoteScannerService quoteScannerService;

    public QuoteScannerController(QuoteScannerService quoteScannerService) {
        this.quoteScannerService = quoteScannerService;
    }

    /**
     * Получить статистику сканера котировок
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(quoteScannerService.getStats());
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
        return ResponseEntity.ok(Map.of("instruments", quoteScannerService.getInstruments(),
                "count", quoteScannerService.getInstruments().size()));
    }

    /**
     * Получить все доступные инструменты с ценами
     */
    @GetMapping("/available-instruments")
    public ResponseEntity<Map<String, Object>> getAvailableInstruments() {
        return ResponseEntity
                .ok(Map.of("instruments", quoteScannerService.getAvailableInstruments(), "names",
                        quoteScannerService.getAvailableInstrumentNames(), "count",
                        quoteScannerService.getAvailableInstruments().size()));
    }
}
