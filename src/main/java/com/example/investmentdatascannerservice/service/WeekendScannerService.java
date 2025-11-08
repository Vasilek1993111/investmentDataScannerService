package com.example.investmentdatascannerservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Управление индексами вынесено в общий менеджер
    private final IndexBarManager indexBar = new IndexBarManager();

    public WeekendScannerService(InstrumentCacheService instrumentCacheService) {
        this.instrumentCacheService = instrumentCacheService;
    }

    @PostConstruct
    public void initializeDefaultIndices() {
        log.info("Initializing default indices for weekend scanner");

        // Добавляем индексы по умолчанию (только по тикеру)
        indexBar.clear();
        addIndex("IMOEX2", "IMOEX2");
        addIndex("IMOEXF", "IMOEXF");
        addIndex("MXZ5", "MXZ5");
        addIndex("BTC", "BTC");
        addIndex("ETH", "ETH");
        addIndex("SBER", "SBER");
        addIndex("SBERP", "SBERP");
        addIndex("SBERF", "SBERF");
        log.info("Weekend scanner initialized with {} default indices",
                indexBar.getCurrentIndices().size());
    }

    /**
     * Получить текущий список индексов для сканера выходного дня
     */
    public List<Map<String, String>> getCurrentIndices() {
        return indexBar.getCurrentIndices();
    }

    /**
     * Добавить новый индекс для сканера выходного дня (по тикеру)
     */
    public boolean addIndex(String name, String displayName) {
        boolean added = indexBar.addIndex(name, displayName, instrumentCacheService);
        if (added) {
            String figi = instrumentCacheService.getFigiByTicker(name);
            log.info("Weekend scanner: Added new index: {} (FIGI: {}, displayName: {})", name,
                    figi != null ? figi : name, displayName);
        } else {
            log.warn("Weekend scanner: Index with ticker '{}' already exists", name);
        }
        return added;
    }

    /**
     * Удалить индекс для сканера выходного дня
     */
    public boolean removeIndex(String ticker) {
        boolean removed = indexBar.removeIndex(ticker);
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
}
