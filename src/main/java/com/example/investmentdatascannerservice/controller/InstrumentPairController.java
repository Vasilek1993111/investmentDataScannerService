package com.example.investmentdatascannerservice.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.dto.InstrumentPair;
import com.example.investmentdatascannerservice.service.InstrumentPairService;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для управления парами инструментов
 */
@Slf4j
@RestController
@RequestMapping("/api/instrument-pairs")
public class InstrumentPairController {

    private final InstrumentPairService instrumentPairService;

    public InstrumentPairController(InstrumentPairService instrumentPairService) {
        this.instrumentPairService = instrumentPairService;
    }

    /**
     * Получить все пары инструментов
     */
    @GetMapping("/all")
    public ResponseEntity<List<InstrumentPair>> getAllPairs() {
        log.debug("Request to get all instrument pairs");
        List<InstrumentPair> pairs = instrumentPairService.getAllPairs();
        log.info("Returning {} instrument pairs", pairs.size());
        return ResponseEntity.ok(pairs);
    }

    /**
     * Добавить новую пару инструментов
     */
    @PostMapping("/add")
    public ResponseEntity<String> addInstrumentPair(@RequestBody InstrumentPair pair) {
        log.info("Request to add instrument pair: pairId={}, firstInstrument={}, secondInstrument={}",
                pair.pairId(), pair.firstInstrument(), pair.firstInstrument());
        try {
            instrumentPairService.addInstrumentPair(pair);
            log.info("Instrument pair added successfully: pairId={}", pair.pairId());
            return ResponseEntity.ok("Пара успешно добавлена");
        } catch (Exception e) {
            log.error("Error adding instrument pair: pairId={}, error={}", pair.pairId(), e.getMessage(), e);
            return ResponseEntity.badRequest().body("Ошибка добавления пары: " + e.getMessage());
        }
    }

    /**
     * Удалить пару инструментов
     */
    @DeleteMapping("/remove/{pairId}")
    public ResponseEntity<String> removeInstrumentPair(@PathVariable String pairId) {
        log.info("Request to remove instrument pair: pairId={}", pairId);
        try {
            instrumentPairService.removeInstrumentPair(pairId);
            log.info("Instrument pair removed successfully: pairId={}", pairId);
            return ResponseEntity.ok("Пара успешно удалена");
        } catch (Exception e) {
            log.error("Error removing instrument pair: pairId={}, error={}", pairId, e.getMessage(), e);
            return ResponseEntity.badRequest().body("Ошибка удаления пары: " + e.getMessage());
        }
    }

    /**
     * Получить статистику пар инструментов
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.debug("Request to get instrument pairs stats");
        Map<String, Object> stats = instrumentPairService.getStats();
        log.debug("Returning instrument pairs stats: {}", stats);
        return ResponseEntity.ok(stats);
    }

    /**
     * Получить текущие цены всех пар
     */
    @GetMapping("/current-prices")
    public ResponseEntity<Map<String, Object>> getCurrentPrices() {
        log.debug("Request to get current prices for all pairs");
        Map<String, Object> prices = instrumentPairService.getCurrentPrices();
        log.debug("Returning current prices for {} pairs", prices.size());
        return ResponseEntity.ok(prices);
    }
}
