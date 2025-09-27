package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.investmentdatascannerservice.entity.ClosePriceEntity;
import com.example.investmentdatascannerservice.entity.ClosePriceEveningSessionEntity;
import com.example.investmentdatascannerservice.entity.OpenPriceEntity;
import com.example.investmentdatascannerservice.repository.ClosePriceEveningSessionRepository;
import com.example.investmentdatascannerservice.repository.ClosePriceRepository;
import com.example.investmentdatascannerservice.repository.OpenPriceRepository;
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
    private final OpenPriceRepository openPriceRepository;

    // In-memory кэш для быстрого доступа - только последние цены
    private final Map<String, BigDecimal> lastClosePricesCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastEveningSessionPricesCache = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> lastOpenPricesCache = new ConcurrentHashMap<>();

    // Кэш для последних доступных дат
    private LocalDate lastClosePriceDate;
    private LocalDate lastEveningSessionDate;
    private LocalDate lastOpenPriceDate;

    @PostConstruct
    public void initializeCache() {
        log.info("Initializing price cache...");
        loadAllClosePrices();
        loadAllEveningSessionPrices();
        loadAllOpenPrices();
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
     * Очистка кэша
     */
    public void clearCache() {
        lastClosePricesCache.clear();
        lastEveningSessionPricesCache.clear();
        lastOpenPricesCache.clear();
        lastClosePriceDate = null;
        lastEveningSessionDate = null;
        lastOpenPriceDate = null;
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
        loadAllOpenPrices();
        log.info("Price cache reloaded successfully");
    }

    /**
     * Загрузка последних цен открытия в кэш
     */
    @Transactional(readOnly = true)
    public void loadAllOpenPrices() {
        try {
            // Получаем последнюю дату с ценами открытия
            Optional<LocalDate> latestDate = openPriceRepository.findLatestPriceDate();
            if (latestDate.isEmpty()) {
                log.warn("No open prices found in database");
                return;
            }

            lastOpenPriceDate = latestDate.get();
            lastOpenPricesCache.clear();

            // Получаем все уникальные FIGI из базы данных
            List<String> allFigis =
                    openPriceRepository.findAll().stream().map(entity -> entity.getId().getFigi())
                            .distinct().collect(Collectors.toList());

            // Получаем последние цены для всех инструментов
            List<OpenPriceEntity> latestOpenPrices =
                    openPriceRepository.findLastOpenPricesByFigis(allFigis);

            for (OpenPriceEntity entity : latestOpenPrices) {
                String figi = entity.getId().getFigi();
                BigDecimal price = entity.getOpenPrice();
                lastOpenPricesCache.put(figi, price);
            }

            log.info("Loaded {} open prices for date: {}", lastOpenPricesCache.size(),
                    lastOpenPriceDate);

        } catch (Exception e) {
            log.error("Error loading open prices", e);
        }
    }



    /**
     * Получение всех цен закрытия из кэша
     */
    public Map<String, BigDecimal> getAllClosePrices() {
        return Map.copyOf(lastClosePricesCache);
    }

    /**
     * Получение всех цен вечерней сессии из кэша
     */
    public Map<String, BigDecimal> getAllEveningSessionPrices() {
        return Map.copyOf(lastEveningSessionPricesCache);
    }

    /**
     * Получение всех цен открытия из кэша
     */
    public Map<String, BigDecimal> getAllOpenPrices() {
        return Map.copyOf(lastOpenPricesCache);
    }

    /**
     * Получение цены открытия для инструмента из кэша
     */
    public BigDecimal getLastOpenPrice(String figi) {
        return lastOpenPricesCache.get(figi);
    }

    /**
     * Получение всех цен для конкретного инструмента
     */
    public Map<String, BigDecimal> getPricesForFigi(String figi) {
        Map<String, BigDecimal> prices = new java.util.HashMap<>();
        prices.put("closePrice", getLastClosePrice(figi));
        prices.put("eveningSessionPrice", getLastEveningSessionPrice(figi));
        prices.put("openPrice", getLastOpenPrice(figi));
        return prices;
    }

    /**
     * Получение последней даты цен закрытия
     */
    public String getLastClosePriceDate() {
        return lastClosePriceDate != null ? lastClosePriceDate.toString() : "N/A";
    }

    /**
     * Получение последней даты цен вечерней сессии
     */
    public String getLastEveningSessionPriceDate() {
        return lastEveningSessionDate != null ? lastEveningSessionDate.toString() : "N/A";
    }

    /**
     * Получение последней даты цен открытия
     */
    public String getLastOpenPriceDate() {
        return lastOpenPriceDate != null ? lastOpenPriceDate.toString() : "N/A";
    }

    /**
     * Получение статистики кэша
     */
    public Map<String, Object> getCacheStats() {
        return Map.of("closePricesCount", lastClosePricesCache.size(), "eveningSessionPricesCount",
                lastEveningSessionPricesCache.size(), "openPricesCount", lastOpenPricesCache.size(),
                "instrumentsWithClosePrices", lastClosePricesCache.size(),
                "instrumentsWithEveningSessionPrices", lastEveningSessionPricesCache.size(),
                "instrumentsWithOpenPrices", lastOpenPricesCache.size(), "lastClosePriceDate",
                lastClosePriceDate != null ? lastClosePriceDate.toString() : "N/A",
                "lastEveningSessionDate",
                lastEveningSessionDate != null ? lastEveningSessionDate.toString() : "N/A",
                "lastOpenPriceDate",
                lastOpenPriceDate != null ? lastOpenPriceDate.toString() : "N/A");
    }
}
