package com.example.investmentdatascannerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.ClosePriceEntity;
import com.example.investmentdatascannerservice.entity.ClosePriceKey;

/**
 * Репозиторий для работы с ценами закрытия инструментов
 */
@Repository
public interface ClosePriceRepository extends JpaRepository<ClosePriceEntity, ClosePriceKey> {

    /**
     * Найти цену закрытия для конкретного инструмента за определенную дату
     */
    @Query("SELECT c FROM ClosePriceEntity c WHERE c.id.figi = :figi AND c.id.priceDate = :priceDate")
    Optional<ClosePriceEntity> findByFigiAndPriceDate(@Param("figi") String figi,
            @Param("priceDate") LocalDate priceDate);

    /**
     * Найти цены закрытия для списка инструментов за определенную дату
     */
    @Query("SELECT c FROM ClosePriceEntity c WHERE c.id.figi IN :figis AND c.id.priceDate = :priceDate")
    List<ClosePriceEntity> findByFigisAndPriceDate(@Param("figis") List<String> figis,
            @Param("priceDate") LocalDate priceDate);

    /**
     * Найти последнюю доступную дату торгов
     */
    @Query("SELECT MAX(c.id.priceDate) FROM ClosePriceEntity c")
    Optional<LocalDate> findLatestPriceDate();

    /**
     * Найти цены закрытия за последний торговый день для списка инструментов
     */
    @Query("SELECT c FROM ClosePriceEntity c WHERE c.id.figi IN :figis AND c.id.priceDate = "
            + "(SELECT MAX(c2.id.priceDate) FROM ClosePriceEntity c2 WHERE c2.id.figi IN :figis)")
    List<ClosePriceEntity> findLatestClosePricesByFigis(@Param("figis") List<String> figis);
}
