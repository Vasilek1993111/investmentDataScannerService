package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.investmentdatascannerservice.entity.ClosePriceEntity;
import com.example.investmentdatascannerservice.entity.ClosePriceEveningSessionEntity;
import com.example.investmentdatascannerservice.repository.ClosePriceEveningSessionRepository;
import com.example.investmentdatascannerservice.repository.ClosePriceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для кэширования цен закрытия, открытия и вечерней сессии
 * 
 * Предоставляет быстрый доступ к историческим ценам инструментов с использованием in-memory кэша и
 * Spring Cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCacheService {

    private final ClosePriceRepository closePriceRepository;
    private final ClosePriceEveningSessionRepository closePriceEveningSessionRepository;

    // In-memory кэш для быстрого доступа - только последние цены
    private final Map<String, BigDecimal> lastClosePricesCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastEveningSessionPricesCache = new ConcurrentHashMap<>();

    // Кэш для последних доступных дат
    private LocalDate lastClosePriceDate;
    private LocalDate lastEveningSessionDate;

    @PostConstruct
    public void initializeCache() {
        log.info("Initializing price cache...");
        loadAllClosePrices();
        loadAllEveningSessionPrices();
        log.info("Price cache initialized successfully");
    }

    /**
     * Загрузка последних цен закрытия в кэш
     */
    @Transactional(readOnly = true)
    public void loadAllClosePrices() {
        try {
            // Получаем последнюю дату с ценами закрытия
            Optional<LocalDate> latestDate = closePriceRepository.findLatestPriceDate();
            if (latestDate.isEmpty()) {
                log.warn("No close prices found in database");
                return;
            }

            lastClosePriceDate = latestDate.get();
            lastClosePricesCache.clear();

            // Получаем все уникальные FIGI из базы данных
            List<String> allFigis =
                    closePriceRepository.findAll().stream().map(entity -> entity.getId().getFigi())
                            .distinct().collect(Collectors.toList());

            // Получаем последние цены для всех инструментов
            List<ClosePriceEntity> latestClosePrices =
                    closePriceRepository.findLatestClosePricesByFigis(allFigis);

            for (ClosePriceEntity entity : latestClosePrices) {
                String figi = entity.getId().getFigi();
                BigDecimal price = entity.getClosePrice();
                lastClosePricesCache.put(figi, price);
            }

            log.info("Loaded {} latest close prices for {} instruments, date: {}",
                    latestClosePrices.size(), lastClosePricesCache.size(), lastClosePriceDate);

        } catch (Exception e) {
            log.error("Error loading latest close prices into cache", e);
        }
    }

    /**
     * Загрузка последних цен вечерней сессии в кэш
     */
    @Transactional(readOnly = true)
    public void loadAllEveningSessionPrices() {
        try {
            // Получаем последнюю дату с ценами вечерней сессии
            LocalDate latestDate = closePriceEveningSessionRepository.findLastPriceDate();
            if (latestDate == null) {
                log.warn("No evening session prices found in database");
                return;
            }

            lastEveningSessionDate = latestDate;
            lastEveningSessionPricesCache.clear();

            // Получаем все цены за последнюю дату
            List<ClosePriceEveningSessionEntity> latestEveningPrices =
                    closePriceEveningSessionRepository.findByPriceDate(latestDate);

            for (ClosePriceEveningSessionEntity entity : latestEveningPrices) {
                String figi = entity.getFigi();
                BigDecimal price = entity.getClosePrice();
                lastEveningSessionPricesCache.put(figi, price);
            }

            log.info("Loaded {} latest evening session prices for {} instruments, date: {}",
                    latestEveningPrices.size(), lastEveningSessionPricesCache.size(),
                    lastEveningSessionDate);

        } catch (Exception e) {
            log.error("Error loading latest evening session prices into cache", e);
        }
    }

    /**
     * Получение цены закрытия из кэша (только последняя цена)
     */
    public BigDecimal getClosePrice(String figi, LocalDate date) {
        // Возвращаем последнюю цену только если запрашиваемая дата совпадает с последней датой
        if (lastClosePriceDate != null && lastClosePriceDate.equals(date)) {
            return lastClosePricesCache.get(figi);
        }
        return null;
    }

    /**
     * Получение цены закрытия вечерней сессии из кэша (только последняя цена)
     */
    public BigDecimal getEveningSessionPrice(String figi, LocalDate date) {
        // Возвращаем последнюю цену только если запрашиваемая дата совпадает с последней датой
        if (lastEveningSessionDate != null && lastEveningSessionDate.equals(date)) {
            return lastEveningSessionPricesCache.get(figi);
        }
        return null;
    }

    /**
     * Получение последней цены закрытия для инструмента
     */
    public BigDecimal getLastClosePrice(String figi) {
        return lastClosePricesCache.get(figi);
    }

    /**
     * Получение последней цены закрытия вечерней сессии для инструмента
     */
    public BigDecimal getLastEveningSessionPrice(String figi) {
        return lastEveningSessionPricesCache.get(figi);
    }

    /**
     * Получение цен закрытия за указанную дату для списка инструментов (только последняя дата)
     */
    @Cacheable(value = "closePrices", key = "#figis.hashCode() + '_' + #date")
    public Map<String, BigDecimal> getClosePricesForDate(List<String> figis, LocalDate date) {
        // Возвращаем цены только если запрашиваемая дата совпадает с последней датой
        if (lastClosePriceDate != null && lastClosePriceDate.equals(date)) {
            return figis.stream().filter(figi -> lastClosePricesCache.containsKey(figi))
                    .collect(Collectors.toMap(figi -> figi, lastClosePricesCache::get));
        }
        return Map.of();
    }

    /**
     * Получение цен вечерней сессии за указанную дату для списка инструментов (только последняя
     * дата)
     */
    @Cacheable(value = "eveningSessionPrices", key = "#figis.hashCode() + '_' + #date")
    public Map<String, BigDecimal> getEveningSessionPricesForDate(List<String> figis,
            LocalDate date) {
        // Возвращаем цены только если запрашиваемая дата совпадает с последней датой
        if (lastEveningSessionDate != null && lastEveningSessionDate.equals(date)) {
            return figis.stream().filter(figi -> lastEveningSessionPricesCache.containsKey(figi))
                    .collect(Collectors.toMap(figi -> figi, lastEveningSessionPricesCache::get));
        }
        return Map.of();
    }

    /**
     * Получение последних цен закрытия для списка инструментов
     */
    @Cacheable(value = "lastClosePrices", key = "#figis.hashCode()")
    public Map<String, BigDecimal> getLastClosePrices(List<String> figis) {
        return figis.stream().filter(figi -> lastClosePricesCache.containsKey(figi))
                .collect(Collectors.toMap(figi -> figi, lastClosePricesCache::get));
    }

    /**
     * Получение последних цен вечерней сессии для списка инструментов
     */
    @Cacheable(value = "lastEveningSessionPrices", key = "#figis.hashCode()")
    public Map<String, BigDecimal> getLastEveningSessionPrices(List<String> figis) {
        return figis.stream().filter(figi -> lastEveningSessionPricesCache.containsKey(figi))
                .collect(Collectors.toMap(figi -> figi, lastEveningSessionPricesCache::get));
    }

    /**
     * Обновление кэша после добавления новых цен (только если это последняя дата)
     */
    public void updateClosePriceCache(String figi, LocalDate date, BigDecimal price) {
        if (lastClosePriceDate == null || date.isAfter(lastClosePriceDate)) {
            // Если это новая последняя дата, очищаем кэш и обновляем дату
            lastClosePricesCache.clear();
            lastClosePriceDate = date;
        }

        // Добавляем цену только если это последняя дата
        if (lastClosePriceDate.equals(date)) {
            lastClosePricesCache.put(figi, price);
        }
    }

    /**
     * Обновление кэша вечерней сессии после добавления новых цен (только если это последняя дата)
     */
    public void updateEveningSessionPriceCache(String figi, LocalDate date, BigDecimal price) {
        if (lastEveningSessionDate == null || date.isAfter(lastEveningSessionDate)) {
            // Если это новая последняя дата, очищаем кэш и обновляем дату
            lastEveningSessionPricesCache.clear();
            lastEveningSessionDate = date;
        }

        // Добавляем цену только если это последняя дата
        if (lastEveningSessionDate.equals(date)) {
            lastEveningSessionPricesCache.put(figi, price);
        }
    }

    /**
     * Очистка кэша
     */
    public void clearCache() {
        lastClosePricesCache.clear();
        lastEveningSessionPricesCache.clear();
        lastClosePriceDate = null;
        lastEveningSessionDate = null;
        log.info("Price cache cleared");
    }

    /**
     * Перезагрузка кэша
     */
    public void reloadCache() {
        log.info("Reloading price cache...");
        clearCache();
        loadAllClosePrices();
        loadAllEveningSessionPrices();
        log.info("Price cache reloaded successfully");
    }

    /**
     * Получение статистики кэша
     */
    public Map<String, Object> getCacheStats() {
        return Map.of("closePricesCount", lastClosePricesCache.size(), "eveningSessionPricesCount",
                lastEveningSessionPricesCache.size(), "instrumentsWithClosePrices",
                lastClosePricesCache.size(), "instrumentsWithEveningSessionPrices",
                lastEveningSessionPricesCache.size(), "lastClosePriceDate",
                lastClosePriceDate != null ? lastClosePriceDate.toString() : "N/A",
                "lastEveningSessionDate",
                lastEveningSessionDate != null ? lastEveningSessionDate.toString() : "N/A");
    }
}
