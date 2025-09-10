package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.entity.ClosePriceEveningSessionEntity;
import com.example.investmentdatascannerservice.repository.ClosePriceEveningSessionRepository;

/**
 * Сервис для работы с ценами закрытия вечерней сессии
 * 
 * Обеспечивает загрузку и кеширование цен закрытия вечерней сессии для использования в утреннем
 * сканере
 */
@Service
public class ClosePriceEveningSessionService {

    private static final Logger log =
            LoggerFactory.getLogger(ClosePriceEveningSessionService.class);

    private final ClosePriceEveningSessionRepository repository;

    // Кеш для хранения цен закрытия вечерней сессии
    private final Map<String, BigDecimal> eveningClosePrices = new HashMap<>();
    private LocalDate lastLoadedDate = null;

    public ClosePriceEveningSessionService(ClosePriceEveningSessionRepository repository) {
        this.repository = repository;
    }

    /**
     * Загрузить цены закрытия вечерней сессии за предыдущий торговый день
     * 
     * @param figis список FIGI инструментов для загрузки
     * @return Map с ценами закрытия (FIGI -> Price)
     */
    public Map<String, BigDecimal> loadEveningClosePricesForPreviousDay(List<String> figis) {
        try {
            log.info("Loading evening close prices for previous trading day...");

            // Получаем последнюю доступную дату
            LocalDate lastDate = repository.findLastPriceDate();
            if (lastDate == null) {
                log.warn("No evening close prices found in database");
                return new HashMap<>();
            }

            log.info("Last available evening close prices date: {}", lastDate);

            // Загружаем цены за последнюю дату
            List<ClosePriceEveningSessionEntity> prices;
            if (figis == null || figis.isEmpty()) {
                prices = repository.findByPriceDate(lastDate);
            } else {
                prices = repository.findByPriceDateAndFigiIn(lastDate, figis);
            }

            // Преобразуем в Map
            Map<String, BigDecimal> result = prices.stream()
                    .collect(Collectors.toMap(ClosePriceEveningSessionEntity::getFigi,
                            ClosePriceEveningSessionEntity::getClosePrice,
                            (existing, replacement) -> replacement // В случае дубликатов берем
                                                                   // последний
                    ));

            // Обновляем кеш
            eveningClosePrices.clear();
            eveningClosePrices.putAll(result);
            lastLoadedDate = lastDate;

            log.info("Loaded {} evening close prices for date {}: {}", result.size(), lastDate,
                    result.keySet());

            return result;

        } catch (Exception e) {
            log.error("Error loading evening close prices for previous day", e);
            return new HashMap<>();
        }
    }

    /**
     * Получить цены закрытия вечерней сессии за указанную дату
     * 
     * @param date дата для получения цен
     * @param figis список FIGI инструментов (может быть null для всех)
     * @return Map с ценами закрытия (FIGI -> Price)
     */
    public Map<String, BigDecimal> getEveningClosePricesForDate(LocalDate date,
            List<String> figis) {
        try {
            log.debug("Getting evening close prices for date: {}, figis: {}", date, figis);

            List<ClosePriceEveningSessionEntity> prices;
            if (figis == null || figis.isEmpty()) {
                prices = repository.findByPriceDate(date);
            } else {
                prices = repository.findByPriceDateAndFigiIn(date, figis);
            }

            Map<String, BigDecimal> result = prices.stream()
                    .collect(Collectors.toMap(ClosePriceEveningSessionEntity::getFigi,
                            ClosePriceEveningSessionEntity::getClosePrice,
                            (existing, replacement) -> replacement));

            log.debug("Found {} evening close prices for date {}", result.size(), date);
            return result;

        } catch (Exception e) {
            log.error("Error getting evening close prices for date: {}", date, e);
            return new HashMap<>();
        }
    }

    /**
     * Получить цену закрытия вечерней сессии для конкретного инструмента
     * 
     * @param figi FIGI инструмента
     * @return цена закрытия или null если не найдена
     */
    public BigDecimal getEveningClosePrice(String figi) {
        return eveningClosePrices.get(figi);
    }

    /**
     * Получить все загруженные цены закрытия вечерней сессии
     * 
     * @return Map с ценами закрытия (FIGI -> Price)
     */
    public Map<String, BigDecimal> getAllEveningClosePrices() {
        return new HashMap<>(eveningClosePrices);
    }

    /**
     * Проверить, загружены ли цены закрытия вечерней сессии
     * 
     * @return true если цены загружены
     */
    public boolean isEveningClosePricesLoaded() {
        return !eveningClosePrices.isEmpty();
    }

    /**
     * Получить дату последних загруженных цен
     * 
     * @return дата последних цен или null если не загружены
     */
    public LocalDate getLastLoadedDate() {
        return lastLoadedDate;
    }

    /**
     * Очистить кеш цен закрытия вечерней сессии
     */
    public void clearCache() {
        eveningClosePrices.clear();
        lastLoadedDate = null;
        log.info("Evening close prices cache cleared");
    }

    /**
     * Обновить цены закрытия вечерней сессии в кеше
     * 
     * @param prices новые цены закрытия
     * @param date дата цен
     */
    public void updateEveningClosePrices(Map<String, BigDecimal> prices, LocalDate date) {
        eveningClosePrices.clear();
        eveningClosePrices.putAll(prices);
        lastLoadedDate = date;
        log.info("Updated evening close prices cache with {} prices for date {}", prices.size(),
                date);
    }

    /**
     * Получить статистику загруженных цен
     * 
     * @return Map со статистикой
     */
    public Map<String, Object> getStats() {
        return Map.of("loadedPricesCount", eveningClosePrices.size(), "lastLoadedDate",
                lastLoadedDate != null ? lastLoadedDate.toString() : "Not loaded", "isLoaded",
                isEveningClosePricesLoaded());
    }
}
