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
import com.example.investmentdatascannerservice.service.MorningScannerService;
import com.example.investmentdatascannerservice.service.PriceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST контроллер для утреннего сканера
 */
@Slf4j
@RestController
@RequestMapping("/api/scanner/morning-scanner")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class MorningScannerController {

    private final MorningScannerService morningScannerService;
    private final PriceCacheService priceCacheService;

    /**
     * Получить текущий список индексов для утреннего сканера
     */
    @GetMapping("/indices")
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
    @PostMapping("/indices/add")
    public ResponseEntity<Map<String, Object>> addMorningScannerIndex(
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
    @DeleteMapping("/indices/remove")
    public ResponseEntity<Map<String, Object>> removeMorningScannerIndex(
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
    @GetMapping("/indices/prices")
    public ResponseEntity<Map<String, Object>> getMorningScannerIndexPrices() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, String>> indices = morningScannerService.getCurrentIndices();
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
            response.put("message", "Цены закрытия для индексов утреннего сканера получены");
        } catch (Exception e) {
            log.error("Error getting morning scanner index prices", e);
            response.put("success", false);
            response.put("message", "Ошибка при получении цен закрытия: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}

