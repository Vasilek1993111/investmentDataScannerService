package com.example.investmentdatascannerservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.TodayVolumeEntity;

/**
 * Репозиторий для работы с today_volume_view
 */
@Repository
public interface TodayVolumeRepository extends JpaRepository<TodayVolumeEntity, String> {

    /**
     * Получить все данные из вью today_volume_view
     */
    @Query("SELECT t FROM TodayVolumeEntity t ORDER BY t.figi")
    List<TodayVolumeEntity> findAllTodayVolumes();

    /**
     * Получить данные только для акций
     */
    @Query("SELECT t FROM TodayVolumeEntity t WHERE t.instrumentType = 'share' ORDER BY t.figi")
    List<TodayVolumeEntity> findTodayVolumesForShares();

    /**
     * Получить данные только для фьючерсов
     */
    @Query("SELECT t FROM TodayVolumeEntity t WHERE t.instrumentType = 'future' ORDER BY t.figi")
    List<TodayVolumeEntity> findTodayVolumesForFutures();

    /**
     * Получить данные с ненулевым объемом выходной биржевой сессии
     */
    @Query("SELECT t FROM TodayVolumeEntity t WHERE t.weekendExchangeSessionVolume > 0 ORDER BY t.weekendExchangeSessionVolume DESC")
    List<TodayVolumeEntity> findTodayVolumesWithWeekendExchangeVolume();
}
