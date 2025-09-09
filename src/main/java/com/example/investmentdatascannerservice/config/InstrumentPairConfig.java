package com.example.investmentdatascannerservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация для пар инструментов
 * 
 * Позволяет настраивать пары инструментов через application.properties
 */
@ConfigurationProperties(prefix = "instrument-pairs")
public class InstrumentPairConfig {

    /**
     * Список пар инструментов
     */
    private List<PairConfig> pairs = List.of();

    // Геттеры и сеттеры
    public List<PairConfig> getPairs() {
        return pairs;
    }

    public void setPairs(List<PairConfig> pairs) {
        this.pairs = pairs;
    }

    /**
     * Конфигурация одной пары инструментов
     */
    public static class PairConfig {
        private String pairId;
        private String firstInstrument;
        private String secondInstrument;
        private String firstInstrumentName;
        private String secondInstrumentName;

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
    }
}
