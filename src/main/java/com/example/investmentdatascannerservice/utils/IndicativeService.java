package com.example.investmentdatascannerservice.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.entity.IndicativeEntity;
import com.example.investmentdatascannerservice.repository.IndicativeRepository;

/**
 * Сервис для работы с индексами из таблицы invest.indicatives
 */
@Service
public class IndicativeService {

    private static final Logger log = LoggerFactory.getLogger(IndicativeService.class);

    private final IndicativeRepository indicativeRepository;

    public IndicativeService(IndicativeRepository indicativeRepository) {
        this.indicativeRepository = indicativeRepository;
    }

    /**
     * Получить все индексы
     */
    public List<IndicativeEntity> getAllIndicatives() {
        try {
            List<IndicativeEntity> indicatives = indicativeRepository.findAllIndicatives();
            log.info("Loaded {} indicatives from database", indicatives.size());
            if (!indicatives.isEmpty()) {
                log.info("First 3 indicatives: {}",
                        indicatives.subList(0, Math.min(3, indicatives.size())).stream()
                                .map(i -> String.format("FIGI=%s, ticker=%s, name=%s, exchange=%s",
                                        i.getFigi(), i.getTicker(), i.getName(), i.getExchange()))
                                .collect(Collectors.toList()));
            }
            return indicatives;
        } catch (Exception e) {
            log.error("Error loading indicatives from database", e);
            return List.of();
        }
    }

    /**
     * Получить список FIGI всех индексов
     */
    public List<String> getAllIndicativeFigis() {
        List<IndicativeEntity> indicatives = getAllIndicatives();
        List<String> figis =
                indicatives.stream().map(IndicativeEntity::getFigi).collect(Collectors.toList());
        log.info("Returning {} FIGIs for indicatives scanning", figis.size());
        return figis;
    }

    /**
     * Получить карту имен индексов (FIGI -> Название)
     */
    public Map<String, String> getIndicativeNames() {
        return getAllIndicatives().stream()
                .collect(
                        Collectors.toMap(IndicativeEntity::getFigi,
                                indicative -> indicative.getName() != null ? indicative.getName()
                                        : indicative.getTicker(),
                                (existing, replacement) -> existing));
    }

    /**
     * Получить карту тикеров индексов (FIGI -> Тикер)
     */
    public Map<String, String> getIndicativeTickers() {
        List<IndicativeEntity> indicatives = getAllIndicatives();
        Map<String, String> tickers =
                indicatives.stream().collect(Collectors.toMap(IndicativeEntity::getFigi,
                        IndicativeEntity::getTicker, (existing, replacement) -> existing));
        log.info("Loaded {} tickers from indicatives database", tickers.size());
        if (!tickers.isEmpty()) {
            log.info("First 5 tickers: {}",
                    tickers.entrySet().stream().limit(5)
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.toList()));
        }
        return tickers;
    }

    /**
     * Получить количество всех индексов
     */
    public long getAllIndicativesCount() {
        try {
            return indicativeRepository.count();
        } catch (Exception e) {
            log.error("Error counting indicatives", e);
            return 0;
        }
    }

    /**
     * Получить индексы по бирже
     */
    public List<IndicativeEntity> getIndicativesByExchange(String exchange) {
        try {
            return indicativeRepository.findByExchange(exchange);
        } catch (Exception e) {
            log.error("Error loading indicatives by exchange: {}", exchange, e);
            return List.of();
        }
    }

    /**
     * Получить индексы по валюте
     */
    public List<IndicativeEntity> getIndicativesByCurrency(String currency) {
        try {
            return indicativeRepository.findByCurrency(currency);
        } catch (Exception e) {
            log.error("Error loading indicatives by currency: {}", currency, e);
            return List.of();
        }
    }

    /**
     * Получить индекс по тикеру
     */
    public IndicativeEntity getIndicativeByTicker(String ticker) {
        try {
            return indicativeRepository.findByTicker(ticker);
        } catch (Exception e) {
            log.error("Error loading indicative by ticker: {}", ticker, e);
            return null;
        }
    }

    /**
     * Получить индексы, доступные для покупки
     */
    public List<IndicativeEntity> getBuyAvailableIndicatives() {
        try {
            return indicativeRepository.findBuyAvailableIndicatives();
        } catch (Exception e) {
            log.error("Error loading buy available indicatives", e);
            return List.of();
        }
    }

    /**
     * Получить индексы, доступные для продажи
     */
    public List<IndicativeEntity> getSellAvailableIndicatives() {
        try {
            return indicativeRepository.findSellAvailableIndicatives();
        } catch (Exception e) {
            log.error("Error loading sell available indicatives", e);
            return List.of();
        }
    }
}
