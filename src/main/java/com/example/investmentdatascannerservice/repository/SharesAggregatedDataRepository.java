package com.example.investmentdatascannerservice.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.SharesAggregatedDataEntity;

@Repository
public interface SharesAggregatedDataRepository
        extends JpaRepository<SharesAggregatedDataEntity, String> {

    /**
     * Найти данные по FIGI
     */
    Optional<SharesAggregatedDataEntity> findByFigi(String figi);

    /**
     * Найти все данные для списка FIGI
     */
    @Query("SELECT s FROM SharesAggregatedDataEntity s WHERE s.figi IN :figis")
    List<SharesAggregatedDataEntity> findByFigiIn(@Param("figis") List<String> figis);

    /**
     * Найти все данные с непустым средним объемом утром
     */
    @Query("SELECT s FROM SharesAggregatedDataEntity s WHERE s.avgVolumeMorning IS NOT NULL")
    List<SharesAggregatedDataEntity> findAllWithMorningVolume();

    /**
     * Подсчитать количество записей с данными по утреннему объему
     */
    @Query("SELECT COUNT(s) FROM SharesAggregatedDataEntity s WHERE s.avgVolumeMorning IS NOT NULL")
    long countWithMorningVolume();
}
