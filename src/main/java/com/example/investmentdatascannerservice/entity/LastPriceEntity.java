package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity для хранения последних цен сделок (last_prices)
 * 
 * Представляет последние цены сделок для инструментов с временными метками.
 */
@Entity
@Table(name = "last_prices", schema = "invest")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastPriceEntity {

    @EmbeddedId
    private LastPriceKey id;

    @Column(precision = 18, scale = 9)
    private BigDecimal price;

    @Column(length = 255)
    private String currency;

    @Column(length = 255)
    private String exchange;

    /**
     * Конструктор для удобного создания entity
     */
    public LastPriceEntity(String figi, LocalDateTime time, BigDecimal price, String currency,
            String exchange) {
        this.id = new LastPriceKey(figi, time);
        this.price = price;
        this.currency = currency;
        this.exchange = exchange;
    }
}

