package com.example.investmentdatascannerservice.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.LastPriceEntity;
import com.example.investmentdatascannerservice.entity.LastPriceKey;

/**
 * Репозиторий для работы с последними ценами сделок (last_prices)
 */
@Repository
public interface LastPriceRepository extends JpaRepository<LastPriceEntity, LastPriceKey> {

    /**
     * Найти последнюю цену для конкретного инструмента Ищет самую последнюю котировку по времени
     */
    @Query("SELECT l FROM LastPriceEntity l WHERE l.id.figi = :figi ORDER BY l.id.time DESC")
    List<LastPriceEntity> findLatestByFigi(@Param("figi") String figi);

    /**
     * Найти последнюю цену для конкретного инструмента (только одна запись)
     */
    @Query(value = "SELECT * FROM invest.last_prices WHERE figi = :figi ORDER BY time DESC LIMIT 1",
            nativeQuery = true)
    Optional<LastPriceEntity> findLatestPriceByFigi(@Param("figi") String figi);

    /**
     * Найти последние цены для всех инструментов Использует DISTINCT ON для получения самой
     * последней записи по каждому figi Если котировки нет в текущем дне, смотрит в прошлые дни
     * (автоматически через ORDER BY time DESC)
     */
    @Query(value = "SELECT DISTINCT ON (lp.figi) lp.figi, lp.time, lp.price, lp.currency, lp.exchange "
            + "FROM invest.last_prices lp " + "ORDER BY lp.figi, lp.time DESC", nativeQuery = true)
    List<Object[]> findLatestPricesForAllFigis();

    /**
     * Найти последние цены для списка инструментов Для каждого figi возвращает самую последнюю
     * запись
     */
    @Query(value = "SELECT DISTINCT ON (figi) figi, time, price, currency, exchange "
            + "FROM invest.last_prices " + "WHERE figi IN :figis " + "ORDER BY figi, time DESC",
            nativeQuery = true)
    List<Object[]> findLatestPricesByFigis(@Param("figis") List<String> figis);

    /**
     * Найти последнюю цену для конкретного инструмента за определенную дату
     */
    @Query(value = "SELECT * FROM invest.last_prices WHERE figi = :figi "
            + "AND DATE(time) = :date ORDER BY time DESC LIMIT 1", nativeQuery = true)
    Optional<LastPriceEntity> findLatestByFigiAndDate(@Param("figi") String figi,
            @Param("date") LocalDate date);

    /**
     * Найти последнюю цену для конкретного инструмента начиная с определенной даты Если нет данных
     * за текущий день, ищет в прошлых днях
     */
    @Query(value = "SELECT * FROM invest.last_prices WHERE figi = :figi "
            + "AND DATE(time) <= :date ORDER BY time DESC LIMIT 1", nativeQuery = true)
    Optional<LastPriceEntity> findLatestByFigiUpToDate(@Param("figi") String figi,
            @Param("date") LocalDate date);

    /**
     * Найти последнюю доступную дату в таблице
     */
    @Query(value = "SELECT MAX(DATE(time)) FROM invest.last_prices", nativeQuery = true)
    Optional<LocalDate> findLatestDate();
}

