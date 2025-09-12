package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shares_aggregated_data")
public class SharesAggregatedDataEntity {

    @Id
    @Column(name = "figi", length = 255, nullable = false)
    private String figi;

    @Column(name = "avg_volume_morning", precision = 18, scale = 2)
    private BigDecimal avgVolumeMorning;

    @Column(name = "avg_volume_weekend", precision = 18, scale = 2)
    private BigDecimal avgVolumeWeekend;

    @Column(name = "total_trading_days", nullable = false, columnDefinition = "integer default 0")
    private Integer totalTradingDays = 0;

    @Column(name = "total_weekend_days", nullable = false, columnDefinition = "integer default 0")
    private Integer totalWeekendDays = 0;

    @Column(name = "last_calculated", nullable = false)
    private LocalDateTime lastCalculated;

    @Column(name = "created_at", nullable = false,
            columnDefinition = "timestamp with time zone default now()")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "timestamp with time zone default now()")
    private LocalDateTime updatedAt;

    // Конструкторы
    public SharesAggregatedDataEntity() {}

    public SharesAggregatedDataEntity(String figi, BigDecimal avgVolumeMorning,
            BigDecimal avgVolumeWeekend, Integer totalTradingDays, Integer totalWeekendDays,
            LocalDateTime lastCalculated, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.figi = figi;
        this.avgVolumeMorning = avgVolumeMorning;
        this.avgVolumeWeekend = avgVolumeWeekend;
        this.totalTradingDays = totalTradingDays;
        this.totalWeekendDays = totalWeekendDays;
        this.lastCalculated = lastCalculated;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Геттеры и сеттеры
    public String getFigi() {
        return figi;
    }

    public void setFigi(String figi) {
        this.figi = figi;
    }

    public BigDecimal getAvgVolumeMorning() {
        return avgVolumeMorning;
    }

    public void setAvgVolumeMorning(BigDecimal avgVolumeMorning) {
        this.avgVolumeMorning = avgVolumeMorning;
    }

    public BigDecimal getAvgVolumeWeekend() {
        return avgVolumeWeekend;
    }

    public void setAvgVolumeWeekend(BigDecimal avgVolumeWeekend) {
        this.avgVolumeWeekend = avgVolumeWeekend;
    }

    public Integer getTotalTradingDays() {
        return totalTradingDays;
    }

    public void setTotalTradingDays(Integer totalTradingDays) {
        this.totalTradingDays = totalTradingDays;
    }

    public Integer getTotalWeekendDays() {
        return totalWeekendDays;
    }

    public void setTotalWeekendDays(Integer totalWeekendDays) {
        this.totalWeekendDays = totalWeekendDays;
    }

    public LocalDateTime getLastCalculated() {
        return lastCalculated;
    }

    public void setLastCalculated(LocalDateTime lastCalculated) {
        this.lastCalculated = lastCalculated;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "SharesAggregatedDataEntity{" + "figi='" + figi + '\'' + ", avgVolumeMorning="
                + avgVolumeMorning + ", avgVolumeWeekend=" + avgVolumeWeekend
                + ", totalTradingDays=" + totalTradingDays + ", totalWeekendDays="
                + totalWeekendDays + ", lastCalculated=" + lastCalculated + ", createdAt="
                + createdAt + ", updatedAt=" + updatedAt + '}';
    }
}
