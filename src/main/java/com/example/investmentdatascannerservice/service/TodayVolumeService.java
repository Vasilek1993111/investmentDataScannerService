package com.example.investmentdatascannerservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.investmentdatascannerservice.entity.TodayVolumeEntity;
import com.example.investmentdatascannerservice.repository.TodayVolumeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Сервис для работы с данными today_volume_view
 * 
 * Загружает и кэширует данные о объемах торгов за сегодняшний день
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodayVolumeService {

    private final TodayVolumeRepository todayVolumeRepository;

    // Кэш для данных выходной биржевой сессии
    private final Map<String, Long> weekendExchangeVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> weekendExchangeCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> weekendExchangeAvgVolumes = new ConcurrentHashMap<>();

    @PostConstruct
    public void initializeTodayVolumeData() {
        log.info("Initializing today volume data from today_volume_view...");
        loadTodayVolumeData();
        log.info("Today volume data initialized successfully");
    }

    /**
     * Загрузка данных из today_volume_view
     */
    @Transactional(readOnly = true)
    public void loadTodayVolumeData() {
        try {
            List<TodayVolumeEntity> todayVolumes = todayVolumeRepository.findAllTodayVolumes();

            // Очищаем кэш
            weekendExchangeVolumes.clear();
            weekendExchangeCandles.clear();
            weekendExchangeAvgVolumes.clear();

            // Загружаем данные выходной биржевой сессии
            for (TodayVolumeEntity entity : todayVolumes) {
                String figi = entity.getFigi();

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
            }

            log.info("Loaded today volume data for {} instruments", todayVolumes.size());
            log.info("Weekend exchange session data: {} volumes, {} candles, {} avg volumes",
                    weekendExchangeVolumes.size(), weekendExchangeCandles.size(),
                    weekendExchangeAvgVolumes.size());

        } catch (Exception e) {
            log.error("Error loading today volume data", e);
        }
    }

    /**
     * Получить объем выходной биржевой сессии для инструмента
     */
    public Long getWeekendExchangeVolume(String figi) {
        return weekendExchangeVolumes.getOrDefault(figi, 0L);
    }

    /**
     * Получить количество свечей выходной биржевой сессии для инструмента
     */
    public Long getWeekendExchangeCandles(String figi) {
        return weekendExchangeCandles.getOrDefault(figi, 0L);
    }

    /**
     * Получить средний объем на свечу выходной биржевой сессии для инструмента
     */
    public BigDecimal getWeekendExchangeAvgVolume(String figi) {
        return weekendExchangeAvgVolumes.getOrDefault(figi, BigDecimal.ZERO);
    }

    /**
     * Получить все объемы выходной биржевой сессии
     */
    public Map<String, Long> getAllWeekendExchangeVolumes() {
        return Map.copyOf(weekendExchangeVolumes);
    }

    /**
     * Получить все свечи выходной биржевой сессии
     */
    public Map<String, Long> getAllWeekendExchangeCandles() {
        return Map.copyOf(weekendExchangeCandles);
    }

    /**
     * Получить все средние объемы выходной биржевой сессии
     */
    public Map<String, BigDecimal> getAllWeekendExchangeAvgVolumes() {
        return Map.copyOf(weekendExchangeAvgVolumes);
    }

    /**
     * Получить статистику загрузки
     */
    public Map<String, Object> getTodayVolumeStats() {
        return Map.of("totalInstruments", weekendExchangeVolumes.size(),
                "instrumentsWithWeekendExchangeVolume",
                weekendExchangeVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum(),
                "totalWeekendExchangeVolume",
                weekendExchangeVolumes.values().stream().mapToLong(Long::longValue).sum(),
                "totalWeekendExchangeCandles",
                weekendExchangeCandles.values().stream().mapToLong(Long::longValue).sum(),
                "avgWeekendExchangeVolume", weekendExchangeVolumes.values().stream()
                        .mapToLong(Long::longValue).average().orElse(0.0));
    }

    /**
     * Перезагрузка данных
     */
    public void reloadTodayVolumeData() {
        log.info("Reloading today volume data...");
        loadTodayVolumeData();
        log.info("Today volume data reloaded successfully");
    }
}
