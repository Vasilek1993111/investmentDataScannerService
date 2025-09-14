package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Entity для таблицы цен закрытия вечерней сессии
 * 
 * Хранит цены закрытия инструментов за вечернюю торговую сессию для использования в утреннем
 * сканере
 */
@Entity
@Table(name = "close_prices_evening_session", schema = "invest")
@IdClass(ClosePriceEveningSessionKey.class)
@Data
public class ClosePriceEveningSessionEntity {

    @Id
    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Id
    @Column(name = "figi", nullable = false, length = 255)
    private String figi;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 9)
    private BigDecimal closePrice;

    @Column(name = "instrument_type", length = 255)
    private String instrumentType;

    @Column(name = "currency", length = 255)
    private String currency;

    @Column(name = "exchange", length = 255)
    private String exchange;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Конструкторы
    public ClosePriceEveningSessionEntity() {}

    public ClosePriceEveningSessionEntity(LocalDate priceDate, String figi, BigDecimal closePrice) {
        this.priceDate = priceDate;
        this.figi = figi;
        this.closePrice = closePrice;
        this.createdAt = LocalDateTime.now();
    }

    public ClosePriceEveningSessionEntity(LocalDate priceDate, String figi, BigDecimal closePrice,
            String instrumentType, String currency, String exchange) {
        this.priceDate = priceDate;
        this.figi = figi;
        this.closePrice = closePrice;
        this.instrumentType = instrumentType;
        this.currency = currency;
        this.exchange = exchange;
        this.createdAt = LocalDateTime.now();
    }
}
