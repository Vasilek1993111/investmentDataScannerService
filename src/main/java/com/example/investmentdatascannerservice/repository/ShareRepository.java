package com.example.investmentdatascannerservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.example.investmentdatascannerservice.entity.ShareEntity;

/**
 * Repository для работы с акциями
 * 
 * Предоставляет методы для поиска и получения информации о торговых инструментах типа "акция".
 */
@Repository
public interface ShareRepository extends JpaRepository<ShareEntity, String> {

    /**
     * Возвращает все уникальные FIGI из таблицы акций
     * 
     * @return список уникальных FIGI акций
     */
    @Query("SELECT DISTINCT s.figi FROM ShareEntity s")
    List<String> findAllDistinctFigi();
}
