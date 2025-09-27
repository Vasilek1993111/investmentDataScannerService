package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.investmentdatascannerservice.entity.HistoryVolumeEntity;
import com.example.investmentdatascannerservice.repository.HistoryVolumeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для работы с историческими данными history_volume_aggregation
 * 
 * Загружает и кэширует исторические данные по объемам торгов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryVolumeService {

    private final HistoryVolumeRepository historyVolumeRepository;

    // Кэш для исторических данных
    private final Map<String, Long> totalVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> totalCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> avgVolumesPerCandle = new ConcurrentHashMap<>();

    // Кэш для утренней сессии
    private final Map<String, Long> morningSessionVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> morningSessionCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> morningAvgVolumes = new ConcurrentHashMap<>();

    // Кэш для основной сессии
    private final Map<String, Long> mainSessionVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> mainSessionCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> mainAvgVolumes = new ConcurrentHashMap<>();

    // Кэш для вечерней сессии
    private final Map<String, Long> eveningSessionVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> eveningSessionCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> eveningAvgVolumes = new ConcurrentHashMap<>();

    // Кэш для выходной биржевой сессии
    private final Map<String, Long> weekendExchangeVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> weekendExchangeCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> weekendExchangeAvgVolumes = new ConcurrentHashMap<>();

    // Кэш для выходной OTC сессии
    private final Map<String, Long> weekendOtcVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> weekendOtcCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> weekendOtcAvgVolumes = new ConcurrentHashMap<>();

    // Кэш для новых полей - количество дней
    private final Map<String, Long> totalDays = new ConcurrentHashMap<>();
    private final Map<String, Long> workingDays = new ConcurrentHashMap<>();
    private final Map<String, Long> weekendDays = new ConcurrentHashMap<>();

    // Кэш для средних объемов за день
    private final Map<String, BigDecimal> morningAvgVolumePerDay = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> mainAvgVolumePerDay = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> eveningAvgVolumePerDay = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> weekendExchangeAvgVolumePerDay =
            new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> weekendOtcAvgVolumePerDay = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeHistoryVolumeData() {
        log.info("Initializing history volume data from history_volume_aggregation...");
        loadHistoryVolumeData();
        log.info("History volume data initialized successfully");
    }

    /**
     * Загрузка данных из history_volume_aggregation
     */
    @Transactional(readOnly = true)
    public void loadHistoryVolumeData() {
        try {
            List<HistoryVolumeEntity> historyVolumes =
                    historyVolumeRepository.findAllHistoryVolumes();

            // Очищаем кэш
            clearAllCaches();

            // Загружаем все данные
            for (HistoryVolumeEntity entity : historyVolumes) {
                String figi = entity.getFigi();

                // Общие данные
                if (entity.getTotalVolume() != null) {
                    totalVolumes.put(figi, entity.getTotalVolume());
                }
                if (entity.getTotalCandles() != null) {
                    totalCandles.put(figi, entity.getTotalCandles());
                }
                if (entity.getAvgVolumePerCandle() != null) {
                    avgVolumesPerCandle.put(figi, entity.getAvgVolumePerCandle());
                }

                // Утренняя сессия
                if (entity.getMorningSessionVolume() != null) {
                    morningSessionVolumes.put(figi, entity.getMorningSessionVolume());
                }
                if (entity.getMorningSessionCandles() != null) {
                    morningSessionCandles.put(figi, entity.getMorningSessionCandles());
                }
                if (entity.getMorningAvgVolumePerCandle() != null) {
                    morningAvgVolumes.put(figi, entity.getMorningAvgVolumePerCandle());
                }

                // Основная сессия
                if (entity.getMainSessionVolume() != null) {
                    mainSessionVolumes.put(figi, entity.getMainSessionVolume());
                }
                if (entity.getMainSessionCandles() != null) {
                    mainSessionCandles.put(figi, entity.getMainSessionCandles());
                }
                if (entity.getMainAvgVolumePerCandle() != null) {
                    mainAvgVolumes.put(figi, entity.getMainAvgVolumePerCandle());
                }

                // Вечерняя сессия
                if (entity.getEveningSessionVolume() != null) {
                    eveningSessionVolumes.put(figi, entity.getEveningSessionVolume());
                }
                if (entity.getEveningSessionCandles() != null) {
                    eveningSessionCandles.put(figi, entity.getEveningSessionCandles());
                }
                if (entity.getEveningAvgVolumePerCandle() != null) {
                    eveningAvgVolumes.put(figi, entity.getEveningAvgVolumePerCandle());
                }

                // Выходная биржевая сессия
                if (entity.getWeekendExchangeSessionVolume() != null) {
                    weekendExchangeVolumes.put(figi, entity.getWeekendExchangeSessionVolume());
                }
                if (entity.getWeekendExchangeSessionCandles() != null) {
                    weekendExchangeCandles.put(figi, entity.getWeekendExchangeSessionCandles());
                }
                if (entity.getWeekendExchangeAvgVolumePerCandle() != null) {
                    weekendExchangeAvgVolumes.put(figi,
                            entity.getWeekendExchangeAvgVolumePerCandle());
                }

                // Выходная OTC сессия
                if (entity.getWeekendOtcSessionVolume() != null) {
                    weekendOtcVolumes.put(figi, entity.getWeekendOtcSessionVolume());
                }
                if (entity.getWeekendOtcSessionCandles() != null) {
                    weekendOtcCandles.put(figi, entity.getWeekendOtcSessionCandles());
                }
                if (entity.getWeekendOtcAvgVolumePerCandle() != null) {
                    weekendOtcAvgVolumes.put(figi, entity.getWeekendOtcAvgVolumePerCandle());
                }

                // Новые поля - количество дней
                if (entity.getTotalDays() != null) {
                    totalDays.put(figi, entity.getTotalDays());
                }
                if (entity.getWorkingDays() != null) {
                    workingDays.put(figi, entity.getWorkingDays());
                }
                if (entity.getWeekendDays() != null) {
                    weekendDays.put(figi, entity.getWeekendDays());
                }

                // Новые поля - средние объемы за день
                if (entity.getMorningAvgVolumePerDay() != null) {
                    morningAvgVolumePerDay.put(figi, entity.getMorningAvgVolumePerDay());
                }
                if (entity.getMainAvgVolumePerDay() != null) {
                    mainAvgVolumePerDay.put(figi, entity.getMainAvgVolumePerDay());
                }
                if (entity.getEveningAvgVolumePerDay() != null) {
                    eveningAvgVolumePerDay.put(figi, entity.getEveningAvgVolumePerDay());
                }
                if (entity.getWeekendExchangeAvgVolumePerDay() != null) {
                    weekendExchangeAvgVolumePerDay.put(figi,
                            entity.getWeekendExchangeAvgVolumePerDay());
                }
                if (entity.getWeekendOtcAvgVolumePerDay() != null) {
                    weekendOtcAvgVolumePerDay.put(figi, entity.getWeekendOtcAvgVolumePerDay());
                }
            }

            log.info("Loaded history volume data for {} instruments", historyVolumes.size());
            logHistoryVolumeStats();

        } catch (Exception e) {
            log.error("Error loading history volume data", e);
        }
    }

    /**
     * Получить общий объем для инструмента
     */
    public Long getTotalVolume(String figi) {
        return totalVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить общее количество свечей для инструмента
     */
    public Long getTotalCandles(String figi) {
        return totalCandles.getOrDefault(figi, 0L);
    }

    /**
     * Получить средний объем на свечу для инструмента
     */
    public BigDecimal getAvgVolumePerCandle(String figi) {
        return avgVolumesPerCandle.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить объем утренней сессии для инструмента
     */
    public Long getMorningSessionVolume(String figi) {
        return morningSessionVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить объем основной сессии для инструмента
     */
    public Long getMainSessionVolume(String figi) {
        return mainSessionVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить объем вечерней сессии для инструмента
     */
    public Long getEveningSessionVolume(String figi) {
        return eveningSessionVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить объем выходной биржевой сессии для инструмента
     */
    public Long getWeekendExchangeVolume(String figi) {
        return weekendExchangeVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить объем выходной OTC сессии для инструмента
     */
    public Long getWeekendOtcVolume(String figi) {
        return weekendOtcVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить все общие объемы
     */
    public Map<String, Long> getAllTotalVolumes() {
        return Map.copyOf(totalVolumes);
    }

    /**
     * Получить все объемы утренней сессии
     */
    public Map<String, Long> getAllMorningSessionVolumes() {
        return Map.copyOf(morningSessionVolumes);
    }

    /**
     * Получить все объемы основной сессии
     */
    public Map<String, Long> getAllMainSessionVolumes() {
        return Map.copyOf(mainSessionVolumes);
    }

    /**
     * Получить все объемы вечерней сессии
     */
    public Map<String, Long> getAllEveningSessionVolumes() {
        return Map.copyOf(eveningSessionVolumes);
    }

    /**
     * Получить все объемы выходной биржевой сессии
     */
    public Map<String, Long> getAllWeekendExchangeVolumes() {
        return Map.copyOf(weekendExchangeVolumes);
    }

    /**
     * Получить все объемы выходной OTC сессии
     */
    public Map<String, Long> getAllWeekendOtcVolumes() {
        return Map.copyOf(weekendOtcVolumes);
    }

    // Новые методы для получения данных о днях
    /**
     * Получить общее количество дней для инструмента
     */
    public Long getTotalDays(String figi) {
        return totalDays.getOrDefault(figi, 0L);
    }

    /**
     * Получить количество рабочих дней для инструмента
     */
    public Long getWorkingDays(String figi) {
        return workingDays.getOrDefault(figi, 0L);
    }

    /**
     * Получить количество выходных дней для инструмента
     */
    public Long getWeekendDays(String figi) {
        return weekendDays.getOrDefault(figi, 0L);
    }

    // Новые методы для получения средних объемов за день
    /**
     * Получить средний объем утренней сессии за день для инструмента
     */
    public BigDecimal getMorningAvgVolumePerDay(String figi) {
        return morningAvgVolumePerDay.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить средний объем основной сессии за день для инструмента
     */
    public BigDecimal getMainAvgVolumePerDay(String figi) {
        return mainAvgVolumePerDay.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить средний объем вечерней сессии за день для инструмента
     */
    public BigDecimal getEveningAvgVolumePerDay(String figi) {
        return eveningAvgVolumePerDay.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить средний объем выходной биржевой сессии за день для инструмента
     */
    public BigDecimal getWeekendExchangeAvgVolumePerDay(String figi) {
        return weekendExchangeAvgVolumePerDay.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить средний объем выходной OTC сессии за день для инструмента
     */
    public BigDecimal getWeekendOtcAvgVolumePerDay(String figi) {
        return weekendOtcAvgVolumePerDay.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить все данные о днях
     */
    public Map<String, Long> getAllTotalDays() {
        return Map.copyOf(totalDays);
    }

    /**
     * Получить все данные о рабочих днях
     */
    public Map<String, Long> getAllWorkingDays() {
        return Map.copyOf(workingDays);
    }

    /**
     * Получить все данные о выходных днях
     */
    public Map<String, Long> getAllWeekendDays() {
        return Map.copyOf(weekendDays);
    }

    /**
     * Получить все средние объемы утренней сессии за день
     */
    public Map<String, BigDecimal> getAllMorningAvgVolumesPerDay() {
        return Map.copyOf(morningAvgVolumePerDay);
    }

    /**
     * Получить все средние объемы основной сессии за день
     */
    public Map<String, BigDecimal> getAllMainAvgVolumesPerDay() {
        return Map.copyOf(mainAvgVolumePerDay);
    }

    /**
     * Получить все средние объемы вечерней сессии за день
     */
    public Map<String, BigDecimal> getAllEveningAvgVolumesPerDay() {
        return Map.copyOf(eveningAvgVolumePerDay);
    }

    /**
     * Получить все средние объемы выходной биржевой сессии за день
     */
    public Map<String, BigDecimal> getAllWeekendExchangeAvgVolumesPerDay() {
        return Map.copyOf(weekendExchangeAvgVolumePerDay);
    }

    /**
     * Получить все средние объемы выходной OTC сессии за день
     */
    public Map<String, BigDecimal> getAllWeekendOtcAvgVolumesPerDay() {
        return Map.copyOf(weekendOtcAvgVolumePerDay);
    }

    /**
     * Получить статистику загрузки
     */
    public Map<String, Object> getHistoryVolumeStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalInstruments", totalVolumes.size());
        stats.put("instrumentsWithTotalVolume",
                totalVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum());
        stats.put("instrumentsWithMorningVolume",
                morningSessionVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum());
        stats.put("instrumentsWithMainVolume",
                mainSessionVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum());
        stats.put("instrumentsWithEveningVolume",
                eveningSessionVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum());
        stats.put("instrumentsWithWeekendExchangeVolume",
                weekendExchangeVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum());
        stats.put("instrumentsWithWeekendOtcVolume",
                weekendOtcVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum());
        stats.put("totalVolume", totalVolumes.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalMorningVolume",
                morningSessionVolumes.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalMainVolume",
                mainSessionVolumes.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalEveningVolume",
                eveningSessionVolumes.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalWeekendExchangeVolume",
                weekendExchangeVolumes.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalWeekendOtcVolume",
                weekendOtcVolumes.values().stream().mapToLong(Long::longValue).sum());

        // Добавляем статистику по новым полям
        stats.put("instrumentsWithTotalDays", totalDays.size());
        stats.put("instrumentsWithWorkingDays", workingDays.size());
        stats.put("instrumentsWithWeekendDays", weekendDays.size());
        stats.put("totalDays", totalDays.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalWorkingDays",
                workingDays.values().stream().mapToLong(Long::longValue).sum());
        stats.put("totalWeekendDays",
                weekendDays.values().stream().mapToLong(Long::longValue).sum());

        return stats;
    }

    /**
     * Перезагрузка данных
     */
    public void reloadHistoryVolumeData() {
        log.info("Reloading history volume data...");
        loadHistoryVolumeData();
        log.info("History volume data reloaded successfully");
    }

    /**
     * Очистка всех кэшей
     */
    private void clearAllCaches() {
        totalVolumes.clear();
        totalCandles.clear();
        avgVolumesPerCandle.clear();
        morningSessionVolumes.clear();
        morningSessionCandles.clear();
        morningAvgVolumes.clear();
        mainSessionVolumes.clear();
        mainSessionCandles.clear();
        mainAvgVolumes.clear();
        eveningSessionVolumes.clear();
        eveningSessionCandles.clear();
        eveningAvgVolumes.clear();
        weekendExchangeVolumes.clear();
        weekendExchangeCandles.clear();
        weekendExchangeAvgVolumes.clear();
        weekendOtcVolumes.clear();
        weekendOtcCandles.clear();
        weekendOtcAvgVolumes.clear();

        // Очистка новых кэшей
        totalDays.clear();
        workingDays.clear();
        weekendDays.clear();
        morningAvgVolumePerDay.clear();
        mainAvgVolumePerDay.clear();
        eveningAvgVolumePerDay.clear();
        weekendExchangeAvgVolumePerDay.clear();
        weekendOtcAvgVolumePerDay.clear();
    }

    /**
     * Логирование статистики
     */
    private void logHistoryVolumeStats() {
        var stats = getHistoryVolumeStats();
        log.info("=== HISTORY VOLUME AGGREGATION STATISTICS ===");
        log.info("Total instruments: {}", stats.get("totalInstruments"));
        log.info("Instruments with total volume: {}", stats.get("instrumentsWithTotalVolume"));
        log.info("Instruments with morning volume: {}", stats.get("instrumentsWithMorningVolume"));
        log.info("Instruments with main volume: {}", stats.get("instrumentsWithMainVolume"));
        log.info("Instruments with evening volume: {}", stats.get("instrumentsWithEveningVolume"));
        log.info("Instruments with weekend exchange volume: {}",
                stats.get("instrumentsWithWeekendExchangeVolume"));
        log.info("Instruments with weekend OTC volume: {}",
                stats.get("instrumentsWithWeekendOtcVolume"));
        log.info("Total volume: {}", stats.get("totalVolume"));
        log.info("Total morning volume: {}", stats.get("totalMorningVolume"));
        log.info("Total main volume: {}", stats.get("totalMainVolume"));
        log.info("Total evening volume: {}", stats.get("totalEveningVolume"));
        log.info("Total weekend exchange volume: {}", stats.get("totalWeekendExchangeVolume"));
        log.info("Total weekend OTC volume: {}", stats.get("totalWeekendOtcVolume"));
        log.info("Instruments with total days: {}", stats.get("instrumentsWithTotalDays"));
        log.info("Instruments with working days: {}", stats.get("instrumentsWithWorkingDays"));
        log.info("Instruments with weekend days: {}", stats.get("instrumentsWithWeekendDays"));
        log.info("Total days: {}", stats.get("totalDays"));
        log.info("Total working days: {}", stats.get("totalWorkingDays"));
        log.info("Total weekend days: {}", stats.get("totalWeekendDays"));
        log.info("=============================================");
    }
}
