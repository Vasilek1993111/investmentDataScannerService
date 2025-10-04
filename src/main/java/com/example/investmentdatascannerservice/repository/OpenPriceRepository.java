package com.example.investmentdatascannerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.OpenPriceEntity;
import com.example.investmentdatascannerservice.entity.OpenPriceKey;

/**
 * Репозиторий для работы с ценами открытия инструментов
 */
@Repository
public interface OpenPriceRepository extends JpaRepository<OpenPriceEntity, OpenPriceKey> {

        /**
         * Найти цену открытия для конкретного инструмента за определенную дату
         */
        @Query("SELECT o FROM OpenPriceEntity o WHERE o.id.figi = :figi AND o.id.priceDate = :priceDate")
        Optional<OpenPriceEntity> findByFigiAndPriceDate(@Param("figi") String figi,
                        @Param("priceDate") LocalDate priceDate);

        /**
         * Найти цены открытия для списка инструментов за определенную дату
         */
        @Query("SELECT o FROM OpenPriceEntity o WHERE o.id.figi IN :figis AND o.id.priceDate = :priceDate")
        List<OpenPriceEntity> findByFigisAndPriceDate(@Param("figis") List<String> figis,
                        @Param("priceDate") LocalDate priceDate);

        /**
         * Найти последнюю доступную дату торгов
         */
        @Query("SELECT MAX(o.id.priceDate) FROM OpenPriceEntity o")
        Optional<LocalDate> findLatestPriceDate();

        /**
         * Найти цены открытия за последний торговый день для списка инструментов
         */
        @Query("SELECT o FROM OpenPriceEntity o WHERE o.id.figi IN :figis AND o.id.priceDate = "
                        + "(SELECT MAX(o2.id.priceDate) FROM OpenPriceEntity o2)")
        List<OpenPriceEntity> findLastOpenPricesByFigis(@Param("figis") List<String> figis);

        /**
         * Найти все цены открытия за последний торговый день
         */
        @Query("SELECT o FROM OpenPriceEntity o WHERE o.id.priceDate = "
                        + "(SELECT MAX(o2.id.priceDate) FROM OpenPriceEntity o2)")
        List<OpenPriceEntity> findLastOpenPrices();

        /**
         * Найти цены открытия за конкретную дату
         */
        List<OpenPriceEntity> findByIdPriceDate(LocalDate priceDate);
}
