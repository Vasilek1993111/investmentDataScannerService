package com.example.investmentdatascannerservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.ShareEntity;

/**
 * Repository для работы с таблицей invest.shares
 */
@Repository
public interface ShareRepository extends JpaRepository<ShareEntity, String> {

    /**
     * Получить все акции
     */
    @Query("SELECT s FROM ShareEntity s ORDER BY s.ticker")
    List<ShareEntity> findAllShares();

    /**
     * Найти акцию по тикеру
     */
    @Query("SELECT s FROM ShareEntity s WHERE s.ticker = :ticker")
    List<ShareEntity> findByTicker(@Param("ticker") String ticker);
}
