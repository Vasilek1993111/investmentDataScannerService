package com.example.investmentdatascannerservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Результат сравнения пары инструментов
 * 
 * Содержит дельту между ценами двух инструментов и метаданные
 */
public class PairComparisonResult {

    private String pairId;
    private String firstInstrument;
    private String secondInstrument;
    private String firstInstrumentName;
    private String secondInstrumentName;
    private BigDecimal firstPrice;
    private BigDecimal secondPrice;
    private BigDecimal delta;
    private BigDecimal deltaPercent;
    private String direction;
    private LocalDateTime timestamp;
    private boolean hasValidPrices;

    // Конструкторы
    public PairComparisonResult() {}

    public PairComparisonResult(String pairId, String firstInstrument, String secondInstrument,
            String firstInstrumentName, String secondInstrumentName, BigDecimal firstPrice,
            BigDecimal secondPrice, BigDecimal delta, BigDecimal deltaPercent, String direction,
            LocalDateTime timestamp, boolean hasValidPrices) {
        this.pairId = pairId;
        this.firstInstrument = firstInstrument;
        this.secondInstrument = secondInstrument;
        this.firstInstrumentName = firstInstrumentName;
        this.secondInstrumentName = secondInstrumentName;
        this.firstPrice = firstPrice;
        this.secondPrice = secondPrice;
        this.delta = delta;
        this.deltaPercent = deltaPercent;
        this.direction = direction;
        this.timestamp = timestamp;
        this.hasValidPrices = hasValidPrices;
    }

    // Геттеры и сеттеры
    public String getPairId() {
        return pairId;
    }

    public void setPairId(String pairId) {
        this.pairId = pairId;
    }

    public String getFirstInstrument() {
        return firstInstrument;
    }

    public void setFirstInstrument(String firstInstrument) {
        this.firstInstrument = firstInstrument;
    }

    public String getSecondInstrument() {
        return secondInstrument;
    }

    public void setSecondInstrument(String secondInstrument) {
        this.secondInstrument = secondInstrument;
    }

    public String getFirstInstrumentName() {
        return firstInstrumentName;
    }

    public void setFirstInstrumentName(String firstInstrumentName) {
        this.firstInstrumentName = firstInstrumentName;
    }

    public String getSecondInstrumentName() {
        return secondInstrumentName;
    }

    public void setSecondInstrumentName(String secondInstrumentName) {
        this.secondInstrumentName = secondInstrumentName;
    }

    public BigDecimal getFirstPrice() {
        return firstPrice;
    }

    public void setFirstPrice(BigDecimal firstPrice) {
        this.firstPrice = firstPrice;
    }

    public BigDecimal getSecondPrice() {
        return secondPrice;
    }

    public void setSecondPrice(BigDecimal secondPrice) {
        this.secondPrice = secondPrice;
    }

    public BigDecimal getDelta() {
        return delta;
    }

    public void setDelta(BigDecimal delta) {
        this.delta = delta;
    }

    public BigDecimal getDeltaPercent() {
        return deltaPercent;
    }

    public void setDeltaPercent(BigDecimal deltaPercent) {
        this.deltaPercent = deltaPercent;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isHasValidPrices() {
        return hasValidPrices;
    }

    public void setHasValidPrices(boolean hasValidPrices) {
        this.hasValidPrices = hasValidPrices;
    }

    @Override
    public String toString() {
        return "PairComparisonResult{" + "pairId='" + pairId + '\'' + ", firstInstrument='"
                + firstInstrument + '\'' + ", secondInstrument='" + secondInstrument + '\''
                + ", firstInstrumentName='" + firstInstrumentName + '\''
                + ", secondInstrumentName='" + secondInstrumentName + '\'' + ", firstPrice="
                + firstPrice + ", secondPrice=" + secondPrice + ", delta=" + delta
                + ", deltaPercent=" + deltaPercent + ", direction='" + direction + '\''
                + ", timestamp=" + timestamp + ", hasValidPrices=" + hasValidPrices + '}';
    }
}
