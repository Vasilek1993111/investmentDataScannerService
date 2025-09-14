package com.example.investmentdatascannerservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Результат сравнения пары инструментов
 * 
 * Содержит дельту между ценами двух инструментов и метаданные
 */
public record PairComparisonResult(String pairId, String firstInstrument, String secondInstrument,
        String firstInstrumentName, String secondInstrumentName, BigDecimal firstPrice,
        BigDecimal secondPrice, BigDecimal delta, BigDecimal deltaPercent, String direction,
        LocalDateTime timestamp, boolean hasValidPrices) {
    // Конструктор по умолчанию для совместимости
    public PairComparisonResult() {
        this(null, null, null, null, null, null, null, null, null, null, null, false);
    }
}
