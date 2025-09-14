package com.example.investmentdatascannerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Entity для таблицы invest.shares
 */
@Entity
@Table(name = "shares", schema = "invest")
@Data
public class ShareEntity {

    @Id
    @Column(name = "figi", nullable = false, length = 50)
    private String figi;

    @Column(name = "ticker", nullable = false, length = 20)
    private String ticker;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "exchange", nullable = false, length = 50)
    private String exchange;

    // Конструкторы
    public ShareEntity() {}

    public ShareEntity(String figi, String ticker, String name, String currency, String exchange) {
        this.figi = figi;
        this.ticker = ticker;
        this.name = name;
        this.currency = currency;
        this.exchange = exchange;
    }
}
