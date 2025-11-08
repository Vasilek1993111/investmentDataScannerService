package com.example.investmentdatascannerservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для управления индексами сканера выходного дня
 * 
 * Предоставляет независимое управление индексами для сканера выходного дня
 */
@Slf4j
@Service
public class WeekendScannerService {

    private final InstrumentCacheService instrumentCacheService;

    // Динамический список индексов для сканера выходного дня
    private final List<IndexConfig> weekendIndices = new CopyOnWriteArrayList<>();

    public WeekendScannerService(InstrumentCacheService instrumentCacheService) {
        this.instrumentCacheService = instrumentCacheService;
    }

    @PostConstruct
    public void initializeDefaultIndices() {
        log.info("Initializing default indices for weekend scanner");

        // Очищаем существующие индексы
        weekendIndices.clear();

        // Добавляем индексы по умолчанию (только по тикеру)
        addIndex("IMOEX2", "IMOEX2");
        addIndex("IMOEX", "IMOEX");
        addIndex("BTC", "BTC");
        addIndex("ETH", "ETH");
        log.info("Weekend scanner initialized with {} default indices", weekendIndices.size());
    }

    /**
     * Получить текущий список индексов для сканера выходного дня
     */
    public List<Map<String, String>> getCurrentIndices() {
        return weekendIndices.stream().map(config -> {
            Map<String, String> index = new HashMap<>();
            index.put("figi", config.figi);
            index.put("name", config.ticker);
            index.put("displayName", config.displayName);
            return index;
        }).collect(Collectors.toList());
    }

    /**
     * Добавить новый индекс для сканера выходного дня (по тикеру)
     */
    public boolean addIndex(String name, String displayName) {
        // Проверяем, не существует ли уже индекс с таким ticker
        boolean exists = weekendIndices.stream().anyMatch(config -> config.ticker.equals(name));

        if (exists) {
            log.warn("Weekend scanner: Index with ticker '{}' already exists", name);
            return false;
        }

        // Получаем реальный FIGI по тикеру из кэша инструментов
        String figi = instrumentCacheService.getFigiByTicker(name);
        if (figi == null) {
            log.warn("Weekend scanner: FIGI not found for ticker '{}', using ticker as FIGI", name);
            figi = name; // Fallback: используем тикер как FIGI
        } else {
            log.debug("Weekend scanner: Found FIGI {} for ticker {}", figi, name);
        }

        IndexConfig newIndex = new IndexConfig(figi, name, displayName);
        weekendIndices.add(newIndex);

        log.info("Weekend scanner: Added new index: {} (FIGI: {}, displayName: {})", name, figi,
                displayName);
        return true;
    }

    /**
     * Удалить индекс для сканера выходного дня
     */
    public boolean removeIndex(String ticker) {
        boolean removed = weekendIndices.removeIf(config -> config.ticker.equals(ticker));

        if (removed) {
            log.info("Weekend scanner: Removed index: {}", ticker);
        } else {
            log.warn("Weekend scanner: Index with ticker '{}' not found", ticker);
        }

        return removed;
    }

    /**
     * Получить FIGI индексов для подписки
     */
    public List<String> getIndexFigis() {
        return weekendIndices.stream().map(config -> config.figi).collect(Collectors.toList());
    }

    /**
     * Получить статистику индексов
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIndices", weekendIndices.size());
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
