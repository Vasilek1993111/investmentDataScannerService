package com.example.investmentdatascannerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Entity для таблицы invest.indicatives Представляет не торгуемые индексы, по которым нужно
 * запрашивать котировки
 */
@Entity
@Table(name = "indicatives", schema = "invest")
@Data
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
}
