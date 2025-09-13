package com.example.investmentdatascannerservice.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.investmentdatascannerservice.service.QuoteScannerService;

/**
 * Контроллер для сканера выходного дня
 * 
 * Предоставляет REST API для управления сканером выходного дня, который работает в субботу и
 * воскресенье с 02:00 до 23:50
 */
@RestController
@RequestMapping("/api/weekend-scanner")
@CrossOrigin(origins = "*")
public class WeekendScannerController {

    @Autowired
    private QuoteScannerService quoteScannerService;

    /**
     * Получить статус сканера выходного дня
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();

        boolean isActive = quoteScannerService.isScannerActive();
        boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();

        response.put("active", isActive);
        response.put("weekendSession", isWeekendSession);
        response.put(
                "message", isActive
                        ? (isWeekendSession ? "Сканер выходного дня активен"
                                : "Сканер активен (тестовый режим)")
                        : "Сканер выходного дня неактивен");

        return ResponseEntity.ok(response);
    }

    /**
     * Запустить сканер выходного дня
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startScanner() {
        Map<String, Object> response = new HashMap<>();

        try {
            quoteScannerService.startScannerIfWeekendSessionTime();
            boolean isActive = quoteScannerService.isScannerActive();
            boolean isWeekendSession = quoteScannerService.checkWeekendSessionTime();

            response.put("success", true);
            response.put("active", isActive);
            response.put("weekendSession", isWeekendSession);
            response.put("message", isActive ? "Сканер выходного дня запущен"
                    : "Сканер выходного дня не может быть запущен (не время сессии)");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Ошибка при запуске сканера: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Остановить сканер выходного дня
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopScanner() {
        Map<String, Object> response = new HashMap<>();

        try {
            // Принудительно останавливаем сканер
            quoteScannerService.stopScanner();
            response.put("success", true);
            response.put("active", false);
            response.put("message", "Сканер выходного дня остановлен");

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
}
