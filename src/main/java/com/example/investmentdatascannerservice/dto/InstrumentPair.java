package com.example.investmentdatascannerservice.dto;

/**
 * DTO для хранения пары инструментов для сравнения
 */
public record InstrumentPair(String pairId, String firstInstrument, String secondInstrument,
        String firstInstrumentName, String secondInstrumentName) {
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
