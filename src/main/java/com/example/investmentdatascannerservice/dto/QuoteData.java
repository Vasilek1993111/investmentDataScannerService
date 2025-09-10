package com.example.investmentdatascannerservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * DTO для хранения данных о котировке инструмента Оптимизирован для максимальной производительности
 * и минимальных задержек
 */
public class QuoteData {
    private final String figi;
    private final String instrumentName;
    private final BigDecimal currentPrice;
    private final BigDecimal previousPrice;
    private final BigDecimal priceChange;
    private final BigDecimal priceChangePercent;
    private final BigDecimal closePrice; // Цена закрытия за предыдущий день
    private final BigDecimal closePriceChange; // Изменение от цены закрытия
    private final BigDecimal closePriceChangePercent; // Изменение в % от основной сессии
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime timestamp;
    private final long volume;
    private final String direction;

    public QuoteData(String figi, String instrumentName, BigDecimal currentPrice,
            BigDecimal previousPrice, LocalDateTime timestamp, long volume, String direction) {
        this(figi, instrumentName, currentPrice, previousPrice, null, timestamp, volume, direction);
    }

    public QuoteData(String figi, String instrumentName, BigDecimal currentPrice,
            BigDecimal previousPrice, BigDecimal closePrice, LocalDateTime timestamp, long volume,
            String direction) {
        this.figi = figi;
        this.instrumentName = instrumentName;
        this.currentPrice = currentPrice;
        this.previousPrice = previousPrice;
        this.closePrice = closePrice;
        this.timestamp = timestamp;
        this.volume = volume;
        this.direction = direction;

        // Вычисляем разницу в цене от предыдущей цены
        if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
            this.priceChange = currentPrice.subtract(previousPrice);
            this.priceChangePercent =
                    this.priceChange.divide(previousPrice, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
        } else {
            this.priceChange = BigDecimal.ZERO;
            this.priceChangePercent = BigDecimal.ZERO;
        }

        // Вычисляем изменение от цены закрытия
        if (closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
            this.closePriceChange = currentPrice.subtract(closePrice);
            this.closePriceChangePercent =
                    this.closePriceChange.divide(closePrice, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
        } else {
            this.closePriceChange = BigDecimal.ZERO;
            this.closePriceChangePercent = BigDecimal.ZERO;
        }
    }

    // Геттеры
    public String getFigi() {
        return figi;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getPreviousPrice() {
        return previousPrice;
    }

    public BigDecimal getPriceChange() {
        return priceChange;
    }

    public BigDecimal getPriceChangePercent() {
        return priceChangePercent;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public long getVolume() {
        return volume;
    }

    public String getDirection() {
        return direction;
    }

    public BigDecimal getClosePrice() {
        return closePrice;
    }

    public BigDecimal getClosePriceChange() {
        return closePriceChange;
    }

    public BigDecimal getClosePriceChangePercent() {
        return closePriceChangePercent;
    }

    @Override
    public String toString() {
        return String.format(
                "QuoteData{figi='%s', price=%s, change=%s (%.2f%%), closeChange=%s (%.2f%%), time=%s}",
                figi, currentPrice, priceChange, priceChangePercent, closePriceChange,
                closePriceChangePercent, timestamp);
    }
}
