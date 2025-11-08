package com.example.investmentdatascannerservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final IndexBarManager indexBar = new IndexBarManager();

    @PostConstruct
    public void initializeDefaultIndices() {
        log.info("Initializing default indices for morning scanner");

        indexBar.clear();
        // Добавляем только IMOEX2 по умолчанию
        addIndex("BBG00KDWPPW3", "IMOEX2", "IMOEX2");

        log.info("Morning scanner initialized with {} default indices",
                indexBar.getCurrentIndices().size());
    }

    /**
     * Получить текущий список индексов для утреннего сканера
     */
    public List<Map<String, String>> getCurrentIndices() {
        return indexBar.getCurrentIndices();
    }

    /**
     * Добавить новый индекс для утреннего сканера (с FIGI)
     */
    public boolean addIndex(String figi, String name, String displayName) {
        boolean added = indexBar.addIndex(figi, name, displayName);
        if (added) {
            log.info("Morning scanner: Added new index: {} (FIGI: {}, displayName: {})", name, figi,
                    displayName);
        } else {
            log.warn("Morning scanner: Index with ticker '{}' already exists", name);
        }
        return added;
    }

    /**
     * Добавить новый индекс для утреннего сканера (без FIGI)
     */
    public boolean addIndex(String name, String displayName) {
        return addIndex(name, name, displayName);
    }

    /**
     * Удалить индекс для утреннего сканера
     */
    public boolean removeIndex(String ticker) {
        boolean removed = indexBar.removeIndex(ticker);
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
        return indexBar.getIndexFigis();
    }

    /**
     * Получить статистику индексов
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIndices", indexBar.getCurrentIndices().size());
        stats.put("indices", getCurrentIndices());
        return stats;
    }

    // Класс конфигурации индекса заменен на IndexBarManager.IndexConfig
}
