package com.example.investmentdatascannerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity для таблицы invest.indicatives Представляет не торгуемые индексы, по которым нужно
 * запрашивать котировки
 */
@Entity
@Table(name = "indicatives", schema = "invest")
public class IndicativeEntity {

    @Id
    @Column(name = "figi", nullable = false, length = 255)
    private String figi;

    @Column(name = "buy_available_flag")
    private Boolean buyAvailableFlag;

    @Column(name = "class_code", length = 255)
    private String classCode;

    @Column(name = "currency", length = 255)
    private String currency;

    @Column(name = "exchange", length = 255)
    private String exchange;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "sell_available_flag")
    private Boolean sellAvailableFlag;

    @Column(name = "ticker", length = 255)
    private String ticker;

    @Column(name = "uid", length = 255)
    private String uid;

    // Конструкторы
    public IndicativeEntity() {}

    public IndicativeEntity(String figi, Boolean buyAvailableFlag, String classCode,
            String currency, String exchange, String name, Boolean sellAvailableFlag, String ticker,
            String uid) {
        this.figi = figi;
        this.buyAvailableFlag = buyAvailableFlag;
        this.classCode = classCode;
        this.currency = currency;
        this.exchange = exchange;
        this.name = name;
        this.sellAvailableFlag = sellAvailableFlag;
        this.ticker = ticker;
        this.uid = uid;
    }

    // Геттеры и сеттеры
    public String getFigi() {
        return figi;
    }

    public void setFigi(String figi) {
        this.figi = figi;
    }

    public Boolean getBuyAvailableFlag() {
        return buyAvailableFlag;
    }

    public void setBuyAvailableFlag(Boolean buyAvailableFlag) {
        this.buyAvailableFlag = buyAvailableFlag;
    }

    public String getClassCode() {
        return classCode;
    }

    public void setClassCode(String classCode) {
        this.classCode = classCode;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getSellAvailableFlag() {
        return sellAvailableFlag;
    }

    public void setSellAvailableFlag(Boolean sellAvailableFlag) {
        this.sellAvailableFlag = sellAvailableFlag;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "IndicativeEntity{" + "figi='" + figi + '\'' + ", buyAvailableFlag="
                + buyAvailableFlag + ", classCode='" + classCode + '\'' + ", currency='" + currency
                + '\'' + ", exchange='" + exchange + '\'' + ", name='" + name + '\''
                + ", sellAvailableFlag=" + sellAvailableFlag + ", ticker='" + ticker + '\''
                + ", uid='" + uid + '\'' + '}';
    }
}
