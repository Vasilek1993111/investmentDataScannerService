package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity для хранения информации о фьючерсах
 * 
 * Представляет справочную информацию о торговых инструментах типа "фьючерс" с основными
 * характеристиками и связями.
 */
@Entity
@Table(name = "futures", schema = "invest")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FutureEntity {

    @Id
    @Column(name = "figi", nullable = false, length = 255)
    private String figi;

    @Column(name = "asset_type", length = 255)
    private String assetType;

    @Column(name = "basic_asset", length = 255)
    private String basicAsset;

    @Column(name = "currency", length = 255)
    private String currency;

    @Column(name = "exchange", length = 255)
    private String exchange;

    @Column(name = "ticker", length = 255)
    private String ticker;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "short_enabled")
    private Boolean shortEnabled;

    @Column(name = "expiration_date")
    private LocalDateTime expirationDate;

    @Column(name = "min_price_increment", precision = 19, scale = 4)
    private BigDecimal minPriceIncrement;

    @Column(name = "lot")
    private Integer lot;

    @Column(name = "basic_asset_size", precision = 18, scale = 9)
    private BigDecimal basicAssetSize;

    // Дополнительные конструкторы для удобства
    public FutureEntity(String figi, String ticker, String assetType, String basicAsset,
            String currency, String exchange) {
        this.figi = figi;
        this.ticker = ticker;
        this.assetType = assetType;
        this.basicAsset = basicAsset;
        this.currency = currency;
        this.exchange = exchange;
    }

    public FutureEntity(String figi, String ticker, String assetType, String basicAsset,
            String currency, String exchange, Boolean shortEnabled, LocalDateTime expirationDate,
            BigDecimal minPriceIncrement, Integer lot, BigDecimal basicAssetSize) {
        this.figi = figi;
        this.ticker = ticker;
        this.assetType = assetType;
        this.basicAsset = basicAsset;
        this.currency = currency;
        this.exchange = exchange;
        this.shortEnabled = shortEnabled;
        this.expirationDate = expirationDate;
        this.minPriceIncrement = minPriceIncrement;
        this.lot = lot;
        this.basicAssetSize = basicAssetSize;
    }
}
