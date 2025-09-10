package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity для хранения цен закрытия инструментов
 * 
 * Представляет цены закрытия торговых инструментов за определенную дату для расчета изменений
 * относительно основной сессии.
 */
@Entity
@Table(name = "close_prices", schema = "invest")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClosePriceEntity {

    @EmbeddedId
    private ClosePriceKey id;

    @Column(nullable = false, length = 255)
    private String instrumentType;

    @Column(nullable = false, precision = 18, scale = 9)
    private BigDecimal closePrice;

    @Column(nullable = false, length = 255)
    private String currency;

    @Column(nullable = false, length = 255)
    private String exchange;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Конструктор для удобного создания entity
     */
    public ClosePriceEntity(String figi, LocalDate priceDate, String instrumentType,
            BigDecimal closePrice, String currency, String exchange) {
        this.id = new ClosePriceKey(priceDate, figi);
        this.instrumentType = instrumentType;
        this.closePrice = closePrice;
        this.currency = currency;
        this.exchange = exchange;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
}
