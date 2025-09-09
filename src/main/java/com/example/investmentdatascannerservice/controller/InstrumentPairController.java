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

/**
 * REST контроллер для управления парами инструментов
 */
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
        return ResponseEntity.ok(instrumentPairService.getAllPairs());
    }

    /**
     * Добавить новую пару инструментов
     */
    @PostMapping("/add")
    public ResponseEntity<String> addInstrumentPair(@RequestBody InstrumentPair pair) {
        try {
            instrumentPairService.addInstrumentPair(pair);
            return ResponseEntity.ok("Пара успешно добавлена");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка добавления пары: " + e.getMessage());
        }
    }

    /**
     * Удалить пару инструментов
     */
    @DeleteMapping("/remove/{pairId}")
    public ResponseEntity<String> removeInstrumentPair(@PathVariable String pairId) {
        try {
            instrumentPairService.removeInstrumentPair(pairId);
            return ResponseEntity.ok("Пара успешно удалена");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Ошибка удаления пары: " + e.getMessage());
        }
    }

    /**
     * Получить статистику пар инструментов
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(instrumentPairService.getStats());
    }

    /**
     * Получить текущие цены всех пар
     */
    @GetMapping("/current-prices")
    public ResponseEntity<Map<String, Object>> getCurrentPrices() {
        return ResponseEntity.ok(instrumentPairService.getCurrentPrices());
    }
}
