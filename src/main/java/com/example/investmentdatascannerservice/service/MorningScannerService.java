package com.example.investmentdatascannerservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для управления индексами утреннего сканера
 * 
 * Предоставляет независимое управление индексами для утреннего сканера
 */
@Slf4j
@Service
public class MorningScannerService {

    // Динамический список индексов для утреннего сканера
    private final List<IndexConfig> morningIndices = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void initializeDefaultIndices() {
        log.info("Initializing default indices for morning scanner");

        // Очищаем существующие индексы
        morningIndices.clear();

        // Добавляем только IMOEX2 по умолчанию
        addIndex("BBG00KDWPPW3", "IMOEX2", "IMOEX2");

        log.info("Morning scanner initialized with {} default indices", morningIndices.size());
    }

    /**
     * Получить текущий список индексов для утреннего сканера
     */
    public List<Map<String, String>> getCurrentIndices() {
        return morningIndices.stream().map(config -> {
            Map<String, String> index = new HashMap<>();
            index.put("figi", config.figi);
            index.put("name", config.ticker);
            index.put("displayName", config.displayName);
            return index;
        }).collect(Collectors.toList());
    }

    /**
     * Добавить новый индекс для утреннего сканера (с FIGI)
     */
    public boolean addIndex(String figi, String name, String displayName) {
        // Проверяем, не существует ли уже индекс с таким ticker
        boolean exists = morningIndices.stream().anyMatch(config -> config.ticker.equals(name));

        if (exists) {
            log.warn("Morning scanner: Index with ticker '{}' already exists", name);
            return false;
        }

        // Добавляем новый индекс
        IndexConfig newIndex = new IndexConfig(figi, name, displayName);
        morningIndices.add(newIndex);

        log.info("Morning scanner: Added new index: {} (FIGI: {}, displayName: {})", name, figi,
                displayName);
        return true;
    }

    /**
     * Добавить новый индекс для утреннего сканера (без FIGI)
     */
    public boolean addIndex(String name, String displayName) {
        // Используем name как figi для совместимости
        return addIndex(name, name, displayName);
    }

    /**
     * Удалить индекс для утреннего сканера
     */
    public boolean removeIndex(String ticker) {
        boolean removed = morningIndices.removeIf(config -> config.ticker.equals(ticker));

        if (removed) {
            log.info("Morning scanner: Removed index: {}", ticker);
        } else {
            log.warn("Morning scanner: Index with ticker '{}' not found", ticker);
        }

        return removed;
    }

    /**
     * Получить FIGI индексов для подписки
     */
    public List<String> getIndexFigis() {
        return morningIndices.stream().map(config -> config.figi).collect(Collectors.toList());
    }

    /**
     * Получить статистику индексов
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIndices", morningIndices.size());
        stats.put("indices", getCurrentIndices());
        return stats;
    }

    /**
     * Класс для конфигурации индекса
     */
    public static class IndexConfig {
        public final String figi;
        public final String ticker;
        public final String displayName;

        public IndexConfig(String figi, String ticker, String displayName) {
            this.figi = figi;
            this.ticker = ticker;
            this.displayName = displayName;
        }
    }
}
