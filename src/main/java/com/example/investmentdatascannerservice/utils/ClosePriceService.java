package com.example.investmentdatascannerservice.utils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.entity.ClosePriceEntity;
import com.example.investmentdatascannerservice.repository.ClosePriceRepository;

/**
 * Сервис для работы с ценами закрытия инструментов
 */
@Service
public class ClosePriceService {

    private static final Logger log = LoggerFactory.getLogger(ClosePriceService.class);

    private final ClosePriceRepository closePriceRepository;

    public ClosePriceService(ClosePriceRepository closePriceRepository) {
        this.closePriceRepository = closePriceRepository;
    }

    /**
     * Получить цены закрытия за предыдущий торговый день для списка инструментов
     * 
     * @param figis список FIGI инструментов
     * @return Map с FIGI в качестве ключа и ценой закрытия в качестве значения
     */
    public Map<String, BigDecimal> getClosePricesForPreviousDay(List<String> figis) {
        Map<String, BigDecimal> closePrices = new HashMap<>();

        try {
            // Получаем последнюю доступную дату торгов
            Optional<LocalDate> latestDate = closePriceRepository.findLatestPriceDate();

            if (latestDate.isEmpty()) {
                log.warn("No close prices found in database");
                return closePrices;
            }

            LocalDate previousDay = latestDate.get();
            log.info("Getting close prices for date: {} for {} instruments", previousDay,
                    figis.size());

            // Получаем цены закрытия за последний торговый день
            List<ClosePriceEntity> closePriceEntities =
                    closePriceRepository.findLatestClosePricesByFigis(figis);

            for (ClosePriceEntity entity : closePriceEntities) {
                closePrices.put(entity.getId().getFigi(), entity.getClosePrice());
                log.debug("Found close price for {}: {}", entity.getId().getFigi(),
                        entity.getClosePrice());
            }

            log.info("Retrieved {} close prices for previous trading day", closePrices.size());

        } catch (Exception e) {
            log.error("Error retrieving close prices", e);
        }

        return closePrices;
    }

    /**
     * Получить цену закрытия для конкретного инструмента за предыдущий торговый день
     * 
     * @param figi идентификатор инструмента
     * @return цена закрытия или null, если не найдена
     */
    public BigDecimal getClosePriceForPreviousDay(String figi) {
        try {
            Optional<LocalDate> latestDate = closePriceRepository.findLatestPriceDate();

            if (latestDate.isEmpty()) {
                log.warn("No close prices found in database for FIGI: {}", figi);
                return null;
            }

            Optional<ClosePriceEntity> closePriceEntity =
                    closePriceRepository.findByFigiAndPriceDate(figi, latestDate.get());

            if (closePriceEntity.isPresent()) {
                log.debug("Found close price for {}: {}", figi,
                        closePriceEntity.get().getClosePrice());
                return closePriceEntity.get().getClosePrice();
            } else {
                log.debug("No close price found for FIGI: {} on date: {}", figi, latestDate.get());
                return null;
            }

        } catch (Exception e) {
            log.error("Error retrieving close price for FIGI: {}", figi, e);
            return null;
        }
    }

    /**
     * Сохранить цену закрытия для инструмента
     * 
     * @param figi идентификатор инструмента
     * @param priceDate дата торгов
     * @param instrumentType тип инструмента
     * @param closePrice цена закрытия
     * @param currency валюта
     * @param exchange биржа
     */
    public void saveClosePrice(String figi, LocalDate priceDate, String instrumentType,
            BigDecimal closePrice, String currency, String exchange) {
        try {
            ClosePriceEntity entity = new ClosePriceEntity(figi, priceDate, instrumentType,
                    closePrice, currency, exchange);
            closePriceRepository.save(entity);
            log.debug("Saved close price for {} on {}: {}", figi, priceDate, closePrice);
        } catch (Exception e) {
            log.error("Error saving close price for FIGI: {}", figi, e);
        }
    }
}
