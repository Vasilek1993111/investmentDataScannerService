package com.example.investmentdatascannerservice.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.ClosePriceEveningSessionEntity;
import com.example.investmentdatascannerservice.entity.ClosePriceEveningSessionKey;

/**
 * Repository для работы с ценами закрытия вечерней сессии
 */
@Repository
public interface ClosePriceEveningSessionRepository
                extends JpaRepository<ClosePriceEveningSessionEntity, ClosePriceEveningSessionKey> {

        /**
         * Получить цены закрытия вечерней сессии за указанную дату
         * 
         * @param priceDate дата для получения цен
         * @return список цен закрытия
         */
        List<ClosePriceEveningSessionEntity> findByPriceDate(LocalDate priceDate);

        /**
         * Получить цены закрытия вечерней сессии за указанную дату для конкретных инструментов
         * 
         * @param priceDate дата для получения цен
         * @param figis список FIGI инструментов
         * @return список цен закрытия
         */
        List<ClosePriceEveningSessionEntity> findByPriceDateAndFigiIn(LocalDate priceDate,
                        List<String> figis);

        /**
         * Получить цены закрытия вечерней сессии за указанную дату в виде Map<FIGI, Price>
         * 
         * @param priceDate дата для получения цен
         * @return Map с ценами закрытия
         */
        @Query("SELECT c.figi, c.closePrice FROM ClosePriceEveningSessionEntity c WHERE c.priceDate = :priceDate")
        List<Object[]> findPricesByDate(@Param("priceDate") LocalDate priceDate);

        /**
         * Получить цены закрытия вечерней сессии за указанную дату для конкретных инструментов в
         * виде Map<FIGI, Price>
         * 
         * @param priceDate дата для получения цен
         * @param figis список FIGI инструментов
         * @return Map с ценами закрытия
         */
        @Query("SELECT c.figi, c.closePrice FROM ClosePriceEveningSessionEntity c WHERE c.priceDate = :priceDate AND c.figi IN :figis")
        List<Object[]> findPricesByDateAndFigis(@Param("priceDate") LocalDate priceDate,
                        @Param("figis") List<String> figis);

        /**
         * Получить последнюю доступную дату с ценами закрытия вечерней сессии
         * 
         * @return последняя дата или null если данных нет
         */
        @Query("SELECT MAX(c.priceDate) FROM ClosePriceEveningSessionEntity c")
        LocalDate findLastPriceDate();

        /**
         * Получить цены закрытия вечерней сессии за последнюю доступную дату
         * 
         * @return список цен закрытия
         */
        @Query("SELECT c FROM ClosePriceEveningSessionEntity c WHERE c.priceDate = (SELECT MAX(c2.priceDate) FROM ClosePriceEveningSessionEntity c2)")
        List<ClosePriceEveningSessionEntity> findLastPrices();

        /**
         * Получить цены закрытия вечерней сессии за последнюю доступную дату для конкретных
         * инструментов
         * 
         * @param figis список FIGI инструментов
         * @return список цен закрытия
         */
        @Query("SELECT c FROM ClosePriceEveningSessionEntity c WHERE c.priceDate = (SELECT MAX(c2.priceDate) FROM ClosePriceEveningSessionEntity c2) AND c.figi IN :figis")
        List<ClosePriceEveningSessionEntity> findLastPricesByFigis(
                        @Param("figis") List<String> figis);

        /**
         * Проверить существование цен закрытия вечерней сессии за указанную дату
         * 
         * @param priceDate дата для проверки
         * @return true если данные существуют
         */
        boolean existsByPriceDate(LocalDate priceDate);

        /**
         * Получить количество записей за указанную дату
         * 
         * @param priceDate дата для подсчета
         * @return количество записей
         */
        long countByPriceDate(LocalDate priceDate);
}
