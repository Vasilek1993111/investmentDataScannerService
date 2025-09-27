package com.example.investmentdatascannerservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.HistoryVolumeEntity;

/**
 * Репозиторий для работы с history_volume_aggregation
 */
@Repository
public interface HistoryVolumeRepository extends JpaRepository<HistoryVolumeEntity, String> {

    /**
     * Получить все данные из материализованного представления history_volume_aggregation
     */
    @Query("SELECT h FROM HistoryVolumeEntity h ORDER BY h.figi")
    List<HistoryVolumeEntity> findAllHistoryVolumes();

    /**
     * Получить данные только для акций
     */
    @Query("SELECT h FROM HistoryVolumeEntity h WHERE h.instrumentType = 'share' ORDER BY h.figi")
    List<HistoryVolumeEntity> findHistoryVolumesForShares();

    /**
     * Получить данные только для фьючерсов
     */
    @Query("SELECT h FROM HistoryVolumeEntity h WHERE h.instrumentType = 'future' ORDER BY h.figi")
    List<HistoryVolumeEntity> findHistoryVolumesForFutures();

    /**
     * Получить данные с ненулевым общим объемом
     */
    @Query("SELECT h FROM HistoryVolumeEntity h WHERE h.totalVolume > 0 ORDER BY h.totalVolume DESC")
    List<HistoryVolumeEntity> findHistoryVolumesWithTotalVolume();

    /**
     * Получить данные с ненулевым объемом выходной биржевой сессии
     */
    @Query("SELECT h FROM HistoryVolumeEntity h WHERE h.weekendExchangeSessionVolume > 0 ORDER BY h.weekendExchangeSessionVolume DESC")
    List<HistoryVolumeEntity> findHistoryVolumesWithWeekendExchangeVolume();

    /**
     * Получить данные с ненулевым объемом утренней сессии
     */
    @Query("SELECT h FROM HistoryVolumeEntity h WHERE h.morningSessionVolume > 0 ORDER BY h.morningSessionVolume DESC")
    List<HistoryVolumeEntity> findHistoryVolumesWithMorningVolume();
}
