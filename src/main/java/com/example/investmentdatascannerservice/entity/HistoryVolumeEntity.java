package com.example.investmentdatascannerservice.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity для представления history_volume_aggregation
 * 
 * Содержит агрегированные исторические данные по объемам торгов
 */
@Entity
@Table(name = "history_volume_aggregation", schema = "invest")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryVolumeEntity {

    @Id
    @Column(name = "figi", nullable = false, length = 255)
    private String figi;

    @Column(name = "instrument_type", length = 50)
    private String instrumentType;

    @Column(name = "total_volume")
    private Long totalVolume;

    @Column(name = "total_candles")
    private Long totalCandles;

    @Column(name = "avg_volume_per_candle", precision = 10, scale = 2)
    private BigDecimal avgVolumePerCandle;

    @Column(name = "morning_session_volume")
    private Long morningSessionVolume;

    @Column(name = "morning_session_candles")
    private Long morningSessionCandles;

    @Column(name = "morning_avg_volume_per_candle", precision = 10, scale = 2)
    private BigDecimal morningAvgVolumePerCandle;

    @Column(name = "main_session_volume")
    private Long mainSessionVolume;

    @Column(name = "main_session_candles")
    private Long mainSessionCandles;

    @Column(name = "main_avg_volume_per_candle", precision = 10, scale = 2)
    private BigDecimal mainAvgVolumePerCandle;

    @Column(name = "evening_session_volume")
    private Long eveningSessionVolume;

    @Column(name = "evening_session_candles")
    private Long eveningSessionCandles;

    @Column(name = "evening_avg_volume_per_candle", precision = 10, scale = 2)
    private BigDecimal eveningAvgVolumePerCandle;

    @Column(name = "weekend_exchange_session_volume")
    private Long weekendExchangeSessionVolume;

    @Column(name = "weekend_exchange_session_candles")
    private Long weekendExchangeSessionCandles;

    @Column(name = "weekend_exchange_avg_volume_per_candle", precision = 10, scale = 2)
    private BigDecimal weekendExchangeAvgVolumePerCandle;

    @Column(name = "weekend_otc_session_volume")
    private Long weekendOtcSessionVolume;

    @Column(name = "weekend_otc_session_candles")
    private Long weekendOtcSessionCandles;

    @Column(name = "weekend_otc_avg_volume_per_candle", precision = 10, scale = 2)
    private BigDecimal weekendOtcAvgVolumePerCandle;

    // Новые поля - количество дней
    @Column(name = "total_days")
    private Long totalDays;

    @Column(name = "working_days")
    private Long workingDays;

    @Column(name = "weekend_days")
    private Long weekendDays;

    // Новые поля - средние объемы за день
    @Column(name = "morning_avg_volume_per_day", precision = 10, scale = 2)
    private BigDecimal morningAvgVolumePerDay;

    @Column(name = "main_avg_volume_per_day", precision = 10, scale = 2)
    private BigDecimal mainAvgVolumePerDay;

    @Column(name = "evening_avg_volume_per_day", precision = 10, scale = 2)
    private BigDecimal eveningAvgVolumePerDay;

    @Column(name = "weekend_exchange_avg_volume_per_day", precision = 10, scale = 2)
    private BigDecimal weekendExchangeAvgVolumePerDay;

    @Column(name = "weekend_otc_avg_volume_per_day", precision = 10, scale = 2)
    private BigDecimal weekendOtcAvgVolumePerDay;

    @Column(name = "first_candle_time")
    private LocalDateTime firstCandleTime;

    @Column(name = "last_candle_time")
    private LocalDateTime lastCandleTime;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
}
