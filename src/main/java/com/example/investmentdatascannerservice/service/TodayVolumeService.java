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

    // Кэш для общих данных
    private final Map<String, Long> totalVolumes = new ConcurrentHashMap<>();
    private final Map<String, Long> totalCandles = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> avgVolumesPerCandle = new ConcurrentHashMap<>();


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
            totalVolumes.clear();
            totalCandles.clear();
            avgVolumesPerCandle.clear();

            // Загружаем все данные
            for (TodayVolumeEntity entity : todayVolumes) {
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

            }

            log.info("Loaded today volume data for {} instruments", todayVolumes.size());

        } catch (Exception e) {
            log.error("Error loading today volume data", e);
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
     * Получить все общие объемы
     */
    public Map<String, Long> getAllTotalVolumes() {
        return Map.copyOf(totalVolumes);
    }


    /**
     * Получить статистику загрузки
     */
    public Map<String, Object> getTodayVolumeStats() {
        return Map.of("totalInstruments", totalVolumes.size(), "instrumentsWithTotalVolume",
                totalVolumes.values().stream().mapToLong(v -> v > 0 ? 1 : 0).sum(), "totalVolume",
                totalVolumes.values().stream().mapToLong(Long::longValue).sum(), "totalCandles",
                totalCandles.values().stream().mapToLong(Long::longValue).sum(), "avgVolume",
                totalVolumes.values().stream().mapToLong(Long::longValue).average().orElse(0.0));
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
