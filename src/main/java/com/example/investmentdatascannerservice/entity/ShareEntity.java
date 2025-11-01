package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
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
    @Column(name = "figi", nullable = false, length = 255)
    private String figi;

    @Column(name = "ticker", length = 255)
    private String ticker;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "currency", length = 255)
    private String currency;

    @Column(name = "exchange", length = 255)
    private String exchange;

    @Column(name = "sector", length = 255)
    private String sector;

    @Column(name = "trading_status", length = 255)
    private String tradingStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "short_enabled")
    private Boolean shortEnabled;

    @Column(name = "asset_uid", length = 255)
    private String assetUid;

    @Column(name = "min_price_increment", precision = 19, scale = 4)
    private BigDecimal minPriceIncrement;

    @Column(name = "lot")
    private Integer lot;

    // Конструкторы
    public ShareEntity() {}

    public ShareEntity(String figi, String ticker, String name, String currency, String exchange) {
        this.figi = figi;
        this.ticker = ticker;
        this.name = name;
        this.currency = currency;
        this.exchange = exchange;
    }

    public ShareEntity(String figi, String ticker, String name, String currency, String exchange,
            String sector, String tradingStatus, Boolean shortEnabled, String assetUid,
            BigDecimal minPriceIncrement, Integer lot) {
        this.figi = figi;
        this.ticker = ticker;
        this.name = name;
        this.currency = currency;
        this.exchange = exchange;
        this.sector = sector;
        this.tradingStatus = tradingStatus;
        this.shortEnabled = shortEnabled;
        this.assetUid = assetUid;
        this.minPriceIncrement = minPriceIncrement;
        this.lot = lot;
    }
}
