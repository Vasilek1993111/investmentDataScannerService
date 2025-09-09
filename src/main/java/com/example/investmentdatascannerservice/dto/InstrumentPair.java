package com.example.investmentdatascannerservice.dto;

/**
 * DTO для хранения пары инструментов для сравнения
 */
public class InstrumentPair {
    private final String pairId;
    private final String firstInstrument;
    private final String secondInstrument;
    private final String firstInstrumentName;
    private final String secondInstrumentName;

    public InstrumentPair(String pairId, String firstInstrument, String secondInstrument,
            String firstInstrumentName, String secondInstrumentName) {
        this.pairId = pairId;
        this.firstInstrument = firstInstrument;
        this.secondInstrument = secondInstrument;
        this.firstInstrumentName = firstInstrumentName;
        this.secondInstrumentName = secondInstrumentName;
    }

    public String getPairId() {
        return pairId;
    }

    public String getFirstInstrument() {
        return firstInstrument;
    }

    public String getSecondInstrument() {
        return secondInstrument;
    }

    public String getFirstInstrumentName() {
        return firstInstrumentName;
    }

    public String getSecondInstrumentName() {
        return secondInstrumentName;
    }

    @Override
    public String toString() {
        return String.format("InstrumentPair{id='%s', first=%s (%s), second=%s (%s)}", pairId,
                firstInstrument, firstInstrumentName, secondInstrument, secondInstrumentName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        InstrumentPair that = (InstrumentPair) obj;
        return pairId.equals(that.pairId);
    }

    @Override
    public int hashCode() {
        return pairId.hashCode();
    }
}
