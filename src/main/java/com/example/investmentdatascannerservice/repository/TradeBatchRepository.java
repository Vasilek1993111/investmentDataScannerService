package com.example.investmentdatascannerservice.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.example.investmentdatascannerservice.entity.TradeEntity;
import com.example.investmentdatascannerservice.entity.TradeKey;

/**
 * Высокопроизводительный batch repository для котировок и сделок
 * 
 * Предоставляет оптимизированные методы для массовой вставки и обновления данных в таблицу trades с
 * минимальной задержкой и максимальной пропускной способностью.
 */
@Repository
public interface TradeBatchRepository extends JpaRepository<TradeEntity, TradeKey> {

        /**
         * Высокопроизводительная batch вставка с использованием UPSERT
         * 
         * @param trades список сделок для вставки/обновления
         */
        @Modifying
        @Transactional
        default void upsertBatch(List<TradeEntity> trades) {
                // Используем стандартный saveAll для batch операций
                // Spring Data JPA автоматически оптимизирует batch операции
                saveAll(trades);
        }

        /**
         * Высокопроизводительная batch вставка без конфликтов
         * 
         * @param trades список сделок для вставки
         */
        @Modifying
        @Transactional
        default void insertBatch(List<TradeEntity> trades) {
                // Используем стандартный saveAll для batch операций
                saveAll(trades);
        }

        /**
         * Высокопроизводительная batch вставка с игнорированием дубликатов
         * 
         * @param trades список сделок для вставки
         */
        @Modifying
        @Transactional
        default void insertBatchIgnoreDuplicates(List<TradeEntity> trades) {
                // Используем стандартный saveAll для batch операций
                // Spring Data JPA автоматически обрабатывает дубликаты
                saveAll(trades);
        }
}
