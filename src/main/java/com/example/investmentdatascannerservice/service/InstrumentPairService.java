package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.example.investmentdatascannerservice.dto.InstrumentPair;
import com.example.investmentdatascannerservice.dto.PairComparisonResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Сервис для работы с парами инструментов и расчета дельт между ними
 * 
 * Обеспечивает отслеживание пар инструментов, получение последних цен и расчет дельт в реальном
 * времени с минимальными задержками.
 */
@Service
public class InstrumentPairService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentPairService.class);

    // Конфигурация производительности
    private static final int PROCESSING_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    // Хранилище пар инструментов
    private final Map<String, InstrumentPair> instrumentPairs = new ConcurrentHashMap<>();

    // Хранилище последних цен для каждого инструмента
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();
    private final Map<String, String> instrumentNames = new ConcurrentHashMap<>();

    // Подписчики на обновления результатов сравнения
    private final Set<Consumer<PairComparisonResult>> comparisonSubscribers =
            new CopyOnWriteArraySet<>();

    // Потоки для обработки
    private final ExecutorService processingExecutor =
            Executors.newFixedThreadPool(PROCESSING_THREADS);

    // Статистика
    private final AtomicLong totalComparisonsProcessed = new AtomicLong(0);
    private final AtomicLong totalComparisonsSent = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("=== INSTRUMENT PAIR SERVICE INITIALIZATION ===");
        log.info("Initializing InstrumentPairService with {} processing threads",
                PROCESSING_THREADS);
        log.info("===============================================");
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down InstrumentPairService...");
        processingExecutor.shutdown();
        log.info("InstrumentPairService shutdown completed. Total processed: {} comparisons",
                totalComparisonsProcessed.get());
    }

    /**
     * Добавление пары инструментов для сравнения
     */
    public void addInstrumentPair(InstrumentPair pair) {
        instrumentPairs.put(pair.getPairId(), pair);
        log.info("Added instrument pair: {}", pair);
    }

    /**
     * Удаление пары инструментов
     */
    public void removeInstrumentPair(String pairId) {
        InstrumentPair removed = instrumentPairs.remove(pairId);
        if (removed != null) {
            log.info("Removed instrument pair: {}", removed);
        }
    }

    /**
     * Получение всех пар инструментов
     */
    public List<InstrumentPair> getAllPairs() {
        return List.copyOf(instrumentPairs.values());
    }

    /**
     * Обновление цены инструмента и пересчет всех связанных пар
     */
    public void updateInstrumentPrice(String figi, BigDecimal price, LocalDateTime timestamp) {
        log.debug("Received price update for {}: {} at {}", figi, price, timestamp);

        processingExecutor.submit(() -> {
            try {
                // Обновляем последнюю цену
                lastPrices.put(figi, price);
                log.debug("Updated price for {}: {}. Total prices stored: {}", figi, price,
                        lastPrices.size());

                // Находим все пары, содержащие этот инструмент
                int pairsFound = 0;
                for (InstrumentPair pair : instrumentPairs.values()) {
                    if (pair.getFirstInstrument().equals(figi)
                            || pair.getSecondInstrument().equals(figi)) {
                        pairsFound++;
                        log.debug("Found pair {} containing instrument {}: first={}, second={}",
                                pair.getPairId(), figi, pair.getFirstInstrument(),
                                pair.getSecondInstrument());
                        calculateAndNotifyComparison(pair, timestamp);
                    }
                }

                if (pairsFound == 0) {
                    log.debug("No pairs found containing instrument {}. Available pairs: {}", figi,
                            instrumentPairs.keySet());
                }

            } catch (Exception e) {
                log.error("Error updating instrument price for {}", figi, e);
            }
        });
    }

    /**
     * Расчет и уведомление о результате сравнения пары
     */
    private void calculateAndNotifyComparison(InstrumentPair pair, LocalDateTime timestamp) {
        try {
            BigDecimal firstPrice = lastPrices.get(pair.getFirstInstrument());
            BigDecimal secondPrice = lastPrices.get(pair.getSecondInstrument());

            log.debug("Calculating comparison for pair {}: first={} ({}), second={} ({})",
                    pair.getPairId(), firstPrice, pair.getFirstInstrument(), secondPrice,
                    pair.getSecondInstrument());

            // Проверяем, что у нас есть цены для обоих инструментов
            if (firstPrice == null || secondPrice == null) {
                log.debug("Missing prices for pair {}: first={}, second={}", pair.getPairId(),
                        firstPrice, secondPrice);
                return;
            }

            // Вычисляем дельту и процентное изменение
            BigDecimal delta = firstPrice.subtract(secondPrice);
            BigDecimal deltaPercent = BigDecimal.ZERO;
            if (secondPrice.compareTo(BigDecimal.ZERO) > 0) {
                deltaPercent = delta.divide(secondPrice, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            // Определяем направление
            String direction = "NEUTRAL";
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                direction = "UP";
            } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                direction = "DOWN";
            }

            // Создаем результат сравнения
            PairComparisonResult result = new PairComparisonResult(pair.getPairId(),
                    pair.getFirstInstrument(), pair.getSecondInstrument(),
                    pair.getFirstInstrumentName(), pair.getSecondInstrumentName(), firstPrice,
                    secondPrice, delta, deltaPercent, direction, timestamp, true);

            totalComparisonsProcessed.incrementAndGet();

            // Уведомляем всех подписчиков
            notifySubscribers(result);

            if (log.isDebugEnabled()) {
                log.debug("Processed comparison: {}", result);
            }

        } catch (Exception e) {
            log.error("Error calculating comparison for pair {}", pair.getPairId(), e);
        }
    }

    /**
     * Уведомление всех подписчиков о новом результате сравнения
     */
    private void notifySubscribers(PairComparisonResult result) {
        log.debug("Notifying {} comparison subscribers about result: {}",
                comparisonSubscribers.size(), result);

        int notifiedCount = 0;
        for (Consumer<PairComparisonResult> subscriber : comparisonSubscribers) {
            try {
                subscriber.accept(result);
                totalComparisonsSent.incrementAndGet();
                notifiedCount++;
                log.debug("Successfully notified comparison subscriber {}", notifiedCount);
            } catch (Exception e) {
                log.warn("Error notifying comparison subscriber", e);
            }
        }
        log.debug("Notified {} comparison subscribers successfully", notifiedCount);
    }

    /**
     * Подписка на обновления результатов сравнения
     */
    public void subscribeToComparisons(Consumer<PairComparisonResult> subscriber) {
        comparisonSubscribers.add(subscriber);
        log.info("New comparison subscriber added. Total subscribers: {}",
                comparisonSubscribers.size());
    }

    /**
     * Отписка от обновлений результатов сравнения
     */
    public void unsubscribeFromComparisons(Consumer<PairComparisonResult> subscriber) {
        comparisonSubscribers.remove(subscriber);
        log.info("Comparison subscriber removed. Total subscribers: {}",
                comparisonSubscribers.size());
    }

    /**
     * Установка имен инструментов
     */
    public void setInstrumentNames(Map<String, String> names) {
        instrumentNames.putAll(names);
        log.info("Updated instrument names: {} instruments", names.size());
    }


    /**
     * Получение статистики сервиса
     */
    public Map<String, Object> getStats() {
        return Map.of("totalComparisonsProcessed", totalComparisonsProcessed.get(),
                "totalComparisonsSent", totalComparisonsSent.get(), "activeSubscribers",
                comparisonSubscribers.size(), "trackedPairs", instrumentPairs.size(),
                "trackedInstruments", lastPrices.size(), "pairs", instrumentPairs.keySet());
    }

    /**
     * Получение текущих цен в виде Map для REST API
     */
    public Map<String, Object> getCurrentPrices() {
        return Map.of("prices", Map.copyOf(lastPrices), "instrumentNames",
                Map.copyOf(instrumentNames), "pairs", instrumentPairs.values(), "count",
                lastPrices.size());
    }

    /**
     * Статистика сервиса пар инструментов
     */
    public static class PairServiceStats {
        private final long totalComparisonsProcessed;
        private final long totalComparisonsSent;
        private final int activeSubscribers;
        private final int trackedPairs;
        private final int trackedInstruments;

        public PairServiceStats(long totalComparisonsProcessed, long totalComparisonsSent,
                int activeSubscribers, int trackedPairs, int trackedInstruments) {
            this.totalComparisonsProcessed = totalComparisonsProcessed;
            this.totalComparisonsSent = totalComparisonsSent;
            this.activeSubscribers = activeSubscribers;
            this.trackedPairs = trackedPairs;
            this.trackedInstruments = trackedInstruments;
        }

        public long getTotalComparisonsProcessed() {
            return totalComparisonsProcessed;
        }

        public long getTotalComparisonsSent() {
            return totalComparisonsSent;
        }

        public int getActiveSubscribers() {
            return activeSubscribers;
        }

        public int getTrackedPairs() {
            return trackedPairs;
        }

        public int getTrackedInstruments() {
            return trackedInstruments;
        }
    }
}
