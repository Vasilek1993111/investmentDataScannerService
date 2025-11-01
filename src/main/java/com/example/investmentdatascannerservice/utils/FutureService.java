package com.example.investmentdatascannerservice.utils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.entity.FutureEntity;
import com.example.investmentdatascannerservice.repository.FutureRepository;

/**
 * Сервис для работы с фьючерсами из таблицы invest.futures
 */
@Service
public class FutureService {

    private static final Logger log = LoggerFactory.getLogger(FutureService.class);

    private final FutureRepository futureRepository;

    public FutureService(FutureRepository futureRepository) {
        this.futureRepository = futureRepository;
    }

    /**
     * Получить все фьючерсы
     */
    public List<FutureEntity> getAllFutures() {
        try {
            List<FutureEntity> futures = futureRepository.findAll();
            log.info("Loaded {} futures from database", futures.size());
            if (!futures.isEmpty()) {
                log.info("First 3 futures: {}", futures.subList(0, Math.min(3, futures.size()))
                        .stream()
                        .map(f -> String.format("FIGI=%s, ticker=%s, basicAsset=%s, exchange=%s",
                                f.getFigi(), f.getTicker(), f.getBasicAsset(), f.getExchange()))
                        .collect(Collectors.toList()));
            }
            return futures;
        } catch (Exception e) {
            log.error("Error loading futures from database", e);
            return List.of();
        }
    }

    /**
     * Получить список FIGI всех фьючерсов
     */
    public List<String> getAllFutureFigis() {
        try {
            List<String> figis = futureRepository.findAllFigis();
            log.info("Loaded {} future FIGIs from database", figis.size());
            if (!figis.isEmpty()) {
                log.info("First 5 future FIGIs: {}", figis.subList(0, Math.min(5, figis.size())));
            }
            return figis;
        } catch (Exception e) {
            log.error("Error loading future FIGIs from database", e);
            return List.of();
        }
    }

    /**
     * Получить карту имен инструментов (FIGI -> Название)
     */
    public Map<String, String> getFutureNames() {
        return getAllFutures().stream()
                .collect(Collectors.toMap(FutureEntity::getFigi,
                        future -> future.getBasicAsset() != null ? future.getBasicAsset()
                                : future.getTicker(),
                        (existing, replacement) -> existing));
    }

    /**
     * Получить карту тикеров (FIGI -> Тикер)
     */
    public Map<String, String> getFutureTickers() {
        List<FutureEntity> futures = getAllFutures();
        Map<String, String> tickers =
                futures.stream().collect(Collectors.toMap(FutureEntity::getFigi,
                        FutureEntity::getTicker, (existing, replacement) -> existing));
        log.info("Loaded {} future tickers from database", tickers.size());
        if (!tickers.isEmpty()) {
            log.info("First 5 future tickers: {}",
                    tickers.entrySet().stream().limit(5)
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.toList()));
        }
        return tickers;
    }

    /**
     * Получить short-флаги по FIGI
     */
    public Map<String, Boolean> getFutureShortFlags() {
        try {
            List<Object[]> rows = futureRepository.findShortFlags();
            Map<String, Boolean> map = rows.stream().collect(Collectors.toMap(r -> (String) r[0],
                    r -> (Boolean) r[1] == null ? false : (Boolean) r[1], (a, b) -> a));
            log.info("Loaded {} future short flags from database", map.size());
            return map;
        } catch (Exception e) {
            log.error("Error loading future short flags", e);
            return Map.of();
        }
    }

    /**
     * Получить количество всех фьючерсов
     */
    public long getAllFuturesCount() {
        try {
            return futureRepository.count();
        } catch (Exception e) {
            log.error("Error counting futures", e);
            return 0;
        }
    }

    /**
     * Найти фьючерсы по тикеру
     */
    public List<FutureEntity> findByTicker(String ticker) {
        try {
            List<FutureEntity> futures = futureRepository.findAll().stream()
                    .filter(f -> ticker.equals(f.getTicker())).collect(Collectors.toList());
            log.info("Found {} futures with ticker: {}", futures.size(), ticker);
            return futures;
        } catch (Exception e) {
            log.error("Error finding futures by ticker: {}", ticker, e);
            return List.of();
        }
    }

    /**
     * Найти фьючерсы по базовому активу
     */
    public List<FutureEntity> findByBasicAsset(String basicAsset) {
        try {
            List<FutureEntity> futures = futureRepository.findAll().stream()
                    .filter(f -> basicAsset.equals(f.getBasicAsset())).collect(Collectors.toList());
            log.info("Found {} futures with basic asset: {}", futures.size(), basicAsset);
            return futures;
        } catch (Exception e) {
            log.error("Error finding futures by basic asset: {}", basicAsset, e);
            return List.of();
        }
    }

    /**
     * Получить фьючерсы по типу актива
     */
    public List<FutureEntity> findByAssetType(String assetType) {
        try {
            List<FutureEntity> futures = futureRepository.findAll().stream()
                    .filter(f -> assetType.equals(f.getAssetType())).collect(Collectors.toList());
            log.info("Found {} futures with asset type: {}", futures.size(), assetType);
            return futures;
        } catch (Exception e) {
            log.error("Error finding futures by asset type: {}", assetType, e);
            return List.of();
        }
    }
}
