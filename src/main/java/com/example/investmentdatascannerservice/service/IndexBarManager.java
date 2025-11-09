package com.example.investmentdatascannerservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import com.example.investmentdatascannerservice.utils.InstrumentCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Универсальный менеджер строки индексов с потокобезопасным хранением отдельного списка индексов на
 * инстанс.
 *
 * Не является Spring-бином по умолчанию — каждый сканер создает свою копию, чтобы не смешивать
 * состояния между сканерами.
 */
public class IndexBarManager {

    private static final Logger log = LoggerFactory.getLogger(IndexBarManager.class);
    private final List<IndexConfig> indices = new CopyOnWriteArrayList<>();

    public void clear() {
        log.debug("Clearing all indices");
        indices.clear();
    }

    public void initializeDefaults(List<IndexConfig> defaults) {
        indices.clear();
        if (defaults != null) {
            indices.addAll(defaults);
            log.info("Initialized {} default indices", defaults.size());
        } else {
            log.debug("No default indices provided");
        }
    }

    public List<Map<String, String>> getCurrentIndices() {
        log.trace("Getting current indices, count: {}", indices.size());
        return indices.stream().map(config -> {
            Map<String, String> index = new HashMap<>();
            index.put("figi", config.figi);
            index.put("name", config.ticker);
            index.put("displayName", config.displayName);
            return index;
        }).collect(Collectors.toList());
    }

    public boolean addIndex(String figi, String name, String displayName) {
        boolean exists = indices.stream().anyMatch(config -> config.ticker.equals(name));
        if (exists) {
            log.debug("Index already exists, skipping: ticker={}", name);
            return false;
        }
        indices.add(new IndexConfig(figi != null ? figi : name, name,
                displayName != null ? displayName : name));
        log.info("Index added: figi={}, ticker={}, displayName={}", figi, name, displayName);
        return true;
    }

    /**
     * Добавление по тикеру с опциональным резолвером FIGI. Если резолвер не передан или FIGI не
     * найден — в качестве FIGI используется тикер.
     */
    public boolean addIndex(String name, String displayName,
            InstrumentCacheService instrumentCacheService) {
        String figi = null;
        if (instrumentCacheService != null && name != null) {
            figi = instrumentCacheService.getFigiByTicker(name);
            log.debug("Resolved FIGI for ticker {}: {}", name, figi);
        }
        return addIndex(figi != null ? figi : name, name, displayName);
    }

    public boolean removeIndex(String ticker) {
        boolean removed = indices.removeIf(config -> config.ticker.equals(ticker));
        if (removed) {
            log.info("Index removed: ticker={}", ticker);
        } else {
            log.debug("Index not found for removal: ticker={}", ticker);
        }
        return removed;
    }

    public List<String> getIndexFigis() {
        log.trace("Getting index FIGIs, count: {}", indices.size());
        return indices.stream().map(config -> config.figi).collect(Collectors.toList());
    }

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


