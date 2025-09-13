package com.example.investmentdatascannerservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.IndicativeEntity;

/**
 * Repository для работы с таблицей invest.indicatives
 */
@Repository
public interface IndicativeRepository extends JpaRepository<IndicativeEntity, String> {

    /**
     * Получить все индексы
     */
    @Query("SELECT i FROM IndicativeEntity i ORDER BY i.ticker")
    List<IndicativeEntity> findAllIndicatives();

    /**
     * Получить индексы по бирже
     */
    @Query("SELECT i FROM IndicativeEntity i WHERE i.exchange = ?1 ORDER BY i.ticker")
    List<IndicativeEntity> findByExchange(String exchange);

    /**
     * Получить индексы по валюте
     */
    @Query("SELECT i FROM IndicativeEntity i WHERE i.currency = ?1 ORDER BY i.ticker")
    List<IndicativeEntity> findByCurrency(String currency);

    /**
     * Получить индекс по тикеру
     */
    @Query("SELECT i FROM IndicativeEntity i WHERE i.ticker = ?1")
    IndicativeEntity findByTicker(String ticker);

    /**
     * Получить индексы, доступные для покупки
     */
    @Query("SELECT i FROM IndicativeEntity i WHERE i.buyAvailableFlag = true ORDER BY i.ticker")
    List<IndicativeEntity> findBuyAvailableIndicatives();

    /**
     * Получить индексы, доступные для продажи
     */
    @Query("SELECT i FROM IndicativeEntity i WHERE i.sellAvailableFlag = true ORDER BY i.ticker")
    List<IndicativeEntity> findSellAvailableIndicatives();
}
