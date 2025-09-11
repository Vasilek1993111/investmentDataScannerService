package com.example.investmentdatascannerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity для таблицы invest.shares
 */
@Entity
@Table(name = "shares", schema = "invest")
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

    // Геттеры и сеттеры
    public String getFigi() {
        return figi;
    }

    public void setFigi(String figi) {
        this.figi = figi;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    @Override
    public String toString() {
        return "ShareEntity{" + "figi='" + figi + '\'' + ", ticker='" + ticker + '\'' + ", name='"
                + name + '\'' + ", currency='" + currency + '\'' + ", exchange='" + exchange + '\''
                + '}';
    }
}
